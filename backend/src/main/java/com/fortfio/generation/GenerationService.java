package com.fortfio.generation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fortfio.account.AccountService;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.LabelTextCheck;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.Workflow;
import com.fortfio.ai.WorkflowInputBuilder;
import com.fortfio.ai.WorkflowInputBuilder.StepRequest;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.FileStorage;
import com.fortfio.media.ProductCompositor;
import com.fortfio.media.RemoteFiles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

	private static final int MAX_WARNING_TOKENS = 5;

	private final GenerationJobRepository jobs;
	private final AccountService accountService;
	private final AiProvider aiProvider;
	private final WorkflowInputBuilder inputBuilder;
	private final ProductCompositor compositor;
	private final FileStorage storage;
	private final RemoteFiles remoteFiles;
	private final AppProperties properties;
	private final TransactionTemplate transactions;

	public GenerationJob create(Long accountId, GenerationSpec spec, Map<String, String> images) {
		return start(accountId, normalize(spec), images, null);
	}

	/** Runs the same request again from the stored copies of its uploaded images (charged again). */
	public GenerationJob retry(Long accountId, UUID jobId) {
		GenerationJob original = get(accountId, jobId);
		if (!original.canRetry()) {
			throw new ApiException(HttpStatus.CONFLICT, original.getStatus() == GenerationStatus.RUNNING
					? "Yêu cầu này vẫn đang chạy"
					: "Yêu cầu này không còn ảnh gốc, hãy tải ảnh lên lại");
		}
		Map<String, String> stored = original.inputs();
		return start(accountId, normalize(original.spec()), toDataUrls(stored), stored);
	}

	private static GenerationSpec normalize(GenerationSpec spec) {
		Workflow workflow = spec.workflow();
		OutputFormat format = workflow.formats().isEmpty() ? null
				: spec.format() == null ? OutputFormat.SQUARE : spec.format();
		Placement placement = workflow.placements().isEmpty() ? null
				: spec.placement() == null ? Placement.BOTTOM_CENTER : spec.placement();
		String productName = workflow.preservesProduct() && spec.productName() != null && !spec.productName().isBlank()
				? spec.productName().trim()
				: null;
		int variants = spec.variants() <= 0 ? 1 : spec.variants();
		return new GenerationSpec(workflow, spec.style(), spec.prompt(), format, placement, productName, variants);
	}

	private GenerationJob start(Long accountId, GenerationSpec spec, Map<String, String> images,
			Map<String, String> storedInputs) {
		List<StepRequest> requests = inputBuilder.firstStage(spec, images);
		int credits = spec.workflow().getCredits() * spec.variants();
		accountService.charge(accountId, credits);

		GenerationJob job = new GenerationJob();
		job.setAccountId(accountId);
		job.apply(spec);
		job.setCreditsCharged(credits);

		try {
			job.setInputs(storedInputs != null ? storedInputs : storeInputs(images));
			submit(job, requests);
		} catch (RuntimeException ex) {
			log.error("Submit failed for {}", spec.workflow(), ex);
			job.fail("Không gửi được yêu cầu tới AI, credit đã được hoàn lại.");
		}
		return persist(job);
	}

	private void submit(GenerationJob job, List<StepRequest> requests) {
		for (StepRequest request : requests) {
			job.addStep(request.kind(), request.model()).attach(aiProvider.submit(request.model(), request.input()));
		}
	}

	private Map<String, String> storeInputs(Map<String, String> images) {
		Map<String, String> stored = new LinkedHashMap<>();
		images.forEach((slot, dataUrl) -> {
			if (dataUrl != null && !dataUrl.isBlank()) {
				stored.put(slot, storage.saveDataUrl(dataUrl));
			}
		});
		return stored;
	}

	private Map<String, String> toDataUrls(Map<String, String> storedUrls) {
		Map<String, String> images = new LinkedHashMap<>();
		storedUrls.forEach((slot, url) -> images.put(slot, toDataUrl(url)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Không tìm thấy ảnh gốc, hãy tải ảnh lên lại"))));
		return images;
	}

	private Optional<String> toDataUrl(String storedUrl) {
		return storage.read(storedUrl).map(data -> {
			String extension = storedUrl.substring(storedUrl.lastIndexOf('.') + 1);
			String mimeType = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
			return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
		});
	}

	public List<GenerationJob> recent(Long accountId, int limit) {
		return jobs.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, Math.clamp(limit, 1, 100)));
	}

	public GenerationJob get(Long accountId, UUID id) {
		return jobs.findByIdAndAccountId(id, accountId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
	}

	@Scheduled(fixedDelayString = "${fortfio.poll-interval-ms:4000}", initialDelayString = "${fortfio.poll-interval-ms:4000}")
	@SchedulerLock(name = "pollRunningJobs")
	public void pollRunningJobs() {
		for (GenerationJob job : jobs.findByStatus(GenerationStatus.RUNNING)) {
			poll(job);
			try {
				persist(job);
			} catch (OptimisticLockingFailureException ex) {
				log.warn("Job {} was updated concurrently, skipping this round", job.getId());
			}
		}
	}

	/**
	 * Saves a job that was RUNNING (or new) and refunds it if it just failed. Both happen in one
	 * transaction guarded by the job's version, so a refund can never be applied twice.
	 */
	GenerationJob persist(GenerationJob job) {
		return transactions.execute(status -> {
			GenerationJob saved = jobs.saveAndFlush(job);
			if (saved.getStatus() == GenerationStatus.FAILED) {
				accountService.refund(saved.getAccountId(), saved.getCreditsCharged());
			}
			return saved;
		});
	}

	private void poll(GenerationJob job) {
		if (Instant.now().isAfter(job.getCreatedAt().plus(properties.jobTimeout()))) {
			job.fail("Quá thời gian xử lý, credit đã được hoàn lại.");
			return;
		}

		for (GenerationStep step : job.getSteps()) {
			if (!step.isCompleted() && !pollStep(job, step)) {
				return;
			}
		}
		if (!job.allStepsCompleted()) {
			return;
		}
		try {
			List<StepRequest> next = inputBuilder.nextStage(job.spec(), toDataUrls(job.inputs()), fetchableOutputs(job));
			if (!next.isEmpty()) {
				submit(job, next);
				return;
			}
		} catch (RuntimeException ex) {
			log.error("Next stage failed for job {}", job.getId(), ex);
			job.fail("Không gửi được yêu cầu tới AI, credit đã được hoàn lại.");
			return;
		}
		finish(job);
	}

	/** Returns false when the job has failed and polling should stop. */
	private boolean pollStep(GenerationJob job, GenerationStep step) {
		AiProvider.Status status;
		try {
			status = aiProvider.status(step.submission());
		} catch (RuntimeException ex) {
			log.warn("Status check failed for job {} step {}, will retry", job.getId(), step.getKind(), ex);
			return true;
		}
		if (status != AiProvider.Status.COMPLETED) {
			return true;
		}

		boolean labelCheck = step.getKind() == StepKind.QC_SOURCE || step.getKind() == StepKind.QC_RESULT;
		try {
			AiProvider.Result result = aiProvider.result(step.submission());
			if (labelCheck) {
				step.completeText(result.text());
				return true;
			}
			List<String> urls = step.getKind() == StepKind.MAIN
					? result.urls().stream().limit(job.getVariants()).toList()
					: result.urls().stream().limit(1).toList();
			if (urls.isEmpty()) {
				job.fail("AI không trả về kết quả, credit đã được hoàn lại.");
				return false;
			}
			step.complete(urls.stream().map(url -> store(job, step, url)).toList());
			return true;
		} catch (RuntimeException ex) {
			if (labelCheck) {
				log.warn("Label check failed for job {}, skipping it", job.getId(), ex);
				step.completeText("");
				return true;
			}
			log.error("Result fetch failed for job {} step {}", job.getId(), step.getKind(), ex);
			job.fail("AI xử lý thất bại, credit đã được hoàn lại.");
			return false;
		}
	}

	private String store(GenerationJob job, GenerationStep step, String url) {
		if (url.startsWith("data:")) {
			return storage.saveDataUrl(url);
		}
		return step.getKind() == StepKind.MAIN ? keepCopy(job, url) : url;
	}

	/** Step outputs in a form the provider can fetch: our own files are inlined as data URIs. */
	private Map<StepKind, List<String>> fetchableOutputs(GenerationJob job) {
		Map<StepKind, List<String>> outputs = new EnumMap<>(StepKind.class);
		for (GenerationStep step : job.getSteps()) {
			List<String> urls = outputs.computeIfAbsent(step.getKind(), kind -> new ArrayList<>());
			step.outputUrls().forEach(url -> urls.add(toDataUrl(url).orElse(url)));
		}
		return outputs;
	}

	/**
	 * Provider result links expire, so final outputs are copied into our storage. Intermediate
	 * steps (cutout, backdrop) are consumed right away and not kept.
	 */
	private String keepCopy(GenerationJob job, String url) {
		if (!remoteFiles.isAllowed(url)) {
			log.warn("Job {}: result host is not allow-listed, keeping the provider link", job.getId());
			return url;
		}
		try {
			RemoteFiles.Download file = remoteFiles.download(url);
			return storage.save(file.data(), file.extension());
		} catch (RuntimeException ex) {
			log.error("Job {}: could not copy result, keeping the provider link", job.getId(), ex);
			return url;
		}
	}

	private void finish(GenerationJob job) {
		if (!job.hasStep(StepKind.BACKDROP)) {
			job.setQualityWarning(labelWarning(job));
			job.succeed(job.stepOutputs(StepKind.MAIN));
			return;
		}
		try {
			String url = compositor.composeAndStore(
					job.stepOutput(StepKind.CUTOUT), job.stepOutput(StepKind.BACKDROP), job.getFormat());
			job.succeed(List.of(url));
		} catch (RuntimeException ex) {
			log.error("Compositing failed for job {}", job.getId(), ex);
			job.fail("Ghép ảnh thất bại, credit đã được hoàn lại. Hãy thử ảnh sản phẩm rõ nét hơn.");
		}
	}

	/** Warns when label text of the uploaded product is missing from a result (OCR comparison). */
	static String labelWarning(GenerationJob job) {
		String source = job.getSteps().stream()
				.filter(s -> s.getKind() == StepKind.QC_SOURCE)
				.map(GenerationStep::getOutputText)
				.findFirst()
				.orElse(null);
		if (source == null || source.isBlank()) {
			return null;
		}
		List<String> results = job.getSteps().stream()
				.filter(s -> s.getKind() == StepKind.QC_RESULT)
				.map(GenerationStep::getOutputText)
				.toList();
		Set<String> missing = new LinkedHashSet<>();
		List<Integer> affected = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			if (results.get(i) == null || results.get(i).isBlank()) {
				continue;
			}
			Set<String> lost = LabelTextCheck.missingTokens(source, results.get(i));
			if (!lost.isEmpty()) {
				missing.addAll(lost);
				affected.add(i + 1);
			}
		}
		if (missing.isEmpty()) {
			return null;
		}
		String where = results.size() > 1 ? "Phương án " + String.join(", ", affected.stream().map(String::valueOf).toList()) + ": " : "";
		String tokens = String.join(", ", missing.stream().limit(MAX_WARNING_TOKENS).toList());
		String message = where + "AI có thể đã đổi chữ trên bao bì (không thấy: " + tokens
				+ "). Hãy kiểm tra kỹ trước khi đăng bán.";
		return message.substring(0, Math.min(message.length(), 500));
	}
}
