package com.fortfio.generation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fortfio.account.AccountService;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.OutputFormat;
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

	private final GenerationJobRepository jobs;
	private final AccountService accountService;
	private final AiProvider aiProvider;
	private final WorkflowInputBuilder inputBuilder;
	private final ProductCompositor compositor;
	private final FileStorage storage;
	private final RemoteFiles remoteFiles;
	private final AppProperties properties;
	private final TransactionTemplate transactions;

	public GenerationJob create(Long accountId, Workflow workflow, String style, String prompt,
			OutputFormat format, Map<String, String> images) {
		OutputFormat effectiveFormat = workflow.formats().isEmpty() ? null
				: format == null ? OutputFormat.SQUARE : format;
		List<StepRequest> requests = inputBuilder.build(workflow, style, prompt, effectiveFormat, images);
		accountService.charge(accountId, workflow.getCredits());

		GenerationJob job = new GenerationJob();
		job.setAccountId(accountId);
		job.setWorkflow(workflow);
		job.setFormat(effectiveFormat);
		job.setStyle(style);
		job.setPrompt(prompt);
		job.setCreditsCharged(workflow.getCredits());

		try {
			for (StepRequest request : requests) {
				job.addStep(request.kind(), request.model())
						.attach(aiProvider.submit(request.model(), request.input()));
			}
		} catch (RuntimeException ex) {
			log.error("Submit failed for {}", workflow, ex);
			job.fail("Không gửi được yêu cầu tới AI, credit đã được hoàn lại.");
		}
		return persist(job);
	}

	public List<GenerationJob> recent(Long accountId, int limit) {
		return jobs.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, Math.clamp(limit, 1, 100)));
	}

	public GenerationJob get(Long accountId, UUID id) {
		return jobs.findByIdAndAccountId(id, accountId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
	}

	@Scheduled(fixedDelayString = "${fortfio.poll-interval-ms:4000}")
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
		if (job.allStepsCompleted()) {
			finish(job);
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

		try {
			List<String> urls = aiProvider.result(step.submission());
			if (urls.isEmpty()) {
				job.fail("AI không trả về kết quả, credit đã được hoàn lại.");
				return false;
			}
			String url = urls.getFirst();
			if (url.startsWith("data:")) {
				step.complete(storage.saveDataUrl(url));
			} else {
				step.complete(step.getKind() == StepKind.MAIN ? keepCopy(job, url) : url);
			}
			return true;
		} catch (RuntimeException ex) {
			log.error("Result fetch failed for job {} step {}", job.getId(), step.getKind(), ex);
			job.fail("AI xử lý thất bại, credit đã được hoàn lại.");
			return false;
		}
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
		if (job.getWorkflow().getPipeline() == Workflow.Pipeline.SINGLE) {
			job.succeed(List.of(job.stepOutput(StepKind.MAIN)));
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
}
