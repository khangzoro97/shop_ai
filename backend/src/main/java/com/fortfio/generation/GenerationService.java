package com.fortfio.generation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
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
import com.fortfio.ai.PromptPlan;
import com.fortfio.ai.PromptWriter;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.VideoOptions;
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
		if (spec.workflow().enhancesResult()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Tính năng này chỉ dùng trên ảnh đã tạo xong");
		}
		return start(accountId, normalize(spec), images, null);
	}

	/** Home page trial: no credits, the caller enforces its own limits. */
	public GenerationJob createFree(Long accountId, GenerationSpec spec, Map<String, String> images) {
		return start(accountId, normalize(spec), images, null, 0);
	}

	/** Relights or upscales one finished image of a job as a new job (charged separately). */
	public GenerationJob enhance(Long accountId, UUID sourceId, Workflow workflow, String style, int variant) {
		if (!workflow.enhancesResult()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Không phải tính năng chỉnh ảnh");
		}
		GenerationJob source = get(accountId, sourceId);
		if (source.getStatus() != GenerationStatus.SUCCEEDED || source.getWorkflow().getOutputType() != Workflow.OutputType.IMAGE) {
			throw new ApiException(HttpStatus.CONFLICT, "Chỉ chỉnh được ảnh đã tạo xong");
		}
		List<String> outputs = source.outputUrlList();
		if (variant < 0 || variant >= outputs.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Không có phương án này");
		}
		String image = toDataUrl(outputs.get(variant))
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Ảnh này không còn trên hệ thống, không chỉnh được"));
		GenerationSpec spec = new GenerationSpec(workflow, style, null, null, null, null, 1);
		return start(accountId, normalize(spec), Map.of("image", image), null);
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
				: spec.format() == null ? workflow.formats().getFirst() : spec.format();
		Placement placement = workflow.placements().isEmpty() ? null
				: spec.placement() == null ? Placement.BOTTOM_CENTER : spec.placement();
		String productName = workflow.preservesProduct() && spec.productName() != null && !spec.productName().isBlank()
				? spec.productName().trim()
				: null;
		int variants = spec.variants() <= 0 ? 1 : spec.variants();
		VideoOptions video = workflow.isVideo() ? VideoOptions.normalize(spec.video()) : null;
		String brief = spec.brief() != null && PromptWriter.supports(workflow) ? spec.brief().trim() : null;
		return new GenerationSpec(workflow, spec.style(), spec.prompt(), format, placement, productName, variants,
				video, brief, brief == null || PromptPlan.EMPTY.equals(spec.plan()) ? null : spec.plan());
	}

	private GenerationJob start(Long accountId, GenerationSpec spec, Map<String, String> images,
			Map<String, String> storedInputs) {
		return start(accountId, spec, images, storedInputs, spec.credits());
	}

	private GenerationJob start(Long accountId, GenerationSpec spec, Map<String, String> images,
			Map<String, String> storedInputs, int credits) {
		List<StepRequest> requests = inputBuilder.firstStage(spec, images);
		if (credits > 0) {
			accountService.charge(accountId, credits);
		}

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

	/**
	 * Newest first; {@code page} is zero-based.
	 *
	 * @param workflows only these workflows; all when empty
	 * @param status    only this status; all when null
	 */
	public Page<GenerationJob> page(Long accountId, Collection<Workflow> workflows, GenerationStatus status, int page,
			int size) {
		return jobs.findByAccountIdAndWorkflowInAndStatusInOrderByCreatedAtDesc(accountId,
				workflows == null || workflows.isEmpty() ? EnumSet.allOf(Workflow.class) : workflows,
				status == null ? EnumSet.allOf(GenerationStatus.class) : EnumSet.of(status),
				PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
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
			if (saved.getStatus() == GenerationStatus.FAILED && saved.getCreditsCharged() > 0) {
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
		applyPlan(job);
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

	/** Stores the plan once the language model has answered; generation then starts from it. */
	private static void applyPlan(GenerationJob job) {
		if (job.getBrief() == null || job.getPromptPlan() != null || !job.hasStep(StepKind.PROMPT)) {
			return;
		}
		String output = job.getSteps().stream()
				.filter(s -> s.getKind() == StepKind.PROMPT)
				.map(GenerationStep::getOutputText)
				.findFirst()
				.orElse(null);
		PromptPlan plan = PromptPlan.parse(output);
		if (plan == PromptPlan.EMPTY) {
			log.warn("Job {}: no usable prompt plan, generating from the description as-is", job.getId());
		}
		job.setPlan(plan);
		if (job.getMusic() != null && plan.music() != null) {
			job.setMusic(plan.music());
		}
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
		// Optional text steps: when they fail the job goes on without them.
		boolean textStep = labelCheck || step.getKind() == StepKind.PROMPT;
		try {
			AiProvider.Result result = aiProvider.result(step.submission());
			if (textStep) {
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
			if (textStep) {
				log.warn("{} step failed for job {}, skipping it", step.getKind(), job.getId(), ex);
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
		int sourceTokens = LabelTextCheck.tokens(source).size();
		Set<String> missing = new LinkedHashSet<>();
		List<Integer> changed = new ArrayList<>();
		List<Integer> unreadable = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			if (results.get(i) == null || results.get(i).isBlank()) {
				continue;
			}
			Set<String> lost = LabelTextCheck.missingTokens(source, results.get(i));
			if (!lost.isEmpty() && lost.size() == sourceTokens) {
				unreadable.add(i + 1);
			} else if (!lost.isEmpty()) {
				missing.addAll(lost);
				changed.add(i + 1);
			}
		}
		List<String> parts = new ArrayList<>();
		if (!changed.isEmpty()) {
			parts.add(where(changed, results.size()) + "AI có thể đã đổi chữ trên bao bì (không thấy: "
					+ String.join(", ", missing.stream().limit(MAX_WARNING_TOKENS).toList()) + ").");
		}
		if (!unreadable.isEmpty()) {
			parts.add(where(unreadable, results.size())
					+ "Không đọc được chữ trên nhãn ở ảnh kết quả, có thể do sản phẩm quá nhỏ hoặc AI đã vẽ lại nhãn.");
		}
		if (parts.isEmpty()) {
			return null;
		}
		String message = String.join(" ", parts) + " Hãy kiểm tra kỹ trước khi đăng bán.";
		return message.substring(0, Math.min(message.length(), 500));
	}

	private static String where(List<Integer> variants, int total) {
		return total > 1 ? "Phương án " + String.join(", ", variants.stream().map(String::valueOf).toList()) + ": " : "";
	}
}
