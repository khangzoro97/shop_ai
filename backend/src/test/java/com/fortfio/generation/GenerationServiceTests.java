package com.fortfio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.AiProvider.Result;
import com.fortfio.ai.AiProvider.Status;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.Workflow;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.LocalFileStorage;
import com.fortfio.media.RemoteFiles;
import com.fortfio.media.TestImages;

/** Core job lifecycle, with the local compositing engine for PRODUCT_EXACT. */
@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage",
		"fortfio.fal.exact-engine=composite"
})
class GenerationServiceTests {

	private static final Map<String, String> PRODUCT = Map.of("product", TestImages.redProductDataUrl());
	private static final Submission SUBMISSION = new Submission("req-1", "https://queue/status", "https://queue/result");

	@Autowired
	private GenerationService service;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private GenerationJobRepository jobs;

	@Autowired
	private AppProperties properties;

	@Autowired
	private LocalFileStorage storage;

	@Autowired
	private RemoteFiles remoteFiles;

	@MockitoBean
	private AiProvider aiProvider;

	private Long accountId;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("shop-" + System.nanoTime() + "@test.vn", "{noop}unused", "Test Shop", 10)).getId();
	}

	@Test
	void successfulJobChargesCreditsAndStoresOutput() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(Result.ofUrls(List.of("https://cdn/out.jpg")));

		GenerationJob job = create(Workflow.PRODUCT_PHOTO, "lifestyle", null, null);
		service.pollRunningJobs();
		assertThat(service.get(accountId, job.getId()).getSteps()).extracting(GenerationStep::getKind)
				.containsExactly(StepKind.MAIN, StepKind.QC_SOURCE, StepKind.QC_RESULT);
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).containsExactly("https://cdn/out.jpg");
		assertThat(done.getQualityWarning()).isNull();
		assertThat(credits()).isEqualTo(10 - Workflow.PRODUCT_PHOTO.getCredits());
	}

	@Test
	void failedResultRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenThrow(new RuntimeException("422 content policy"));

		GenerationJob job = create(Workflow.PRODUCT_PHOTO, null, null, null);
		service.pollRunningJobs();

		assertThat(service.get(accountId, job.getId()).getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void submitFailureRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenThrow(new RuntimeException("timeout"));

		GenerationJob job = create(Workflow.PRODUCT_PHOTO, null, null, null);

		assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void rejectsWhenNotEnoughCredits() {
		assertThatThrownBy(() -> create(Workflow.PRODUCT_VIDEO, null, null, null))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.PAYMENT_REQUIRED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void rejectsMissingImageBeforeCharging() {
		assertThatThrownBy(() -> create(Workflow.VIRTUAL_TRYON, null, null, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("người mẫu");
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void compositeJobKeepsProductAndRendersRequestedFormat() throws IOException {
		Submission cutout = new Submission("cut", "s1", "r1");
		Submission backdrop = new Submission("bg", "s2", "r2");
		when(aiProvider.submit(eq(properties.fal().backgroundRemovalModel()), anyMap())).thenReturn(cutout);
		when(aiProvider.submit(eq(properties.fal().backdropModel()), anyMap())).thenReturn(backdrop);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(cutout)).thenReturn(Result.ofUrls(List.of(TestImages.redProductDataUrl())));
		when(aiProvider.result(backdrop)).thenReturn(Result.ofUrls(List.of(TestImages.blueBackdropDataUrl())));

		GenerationJob job = create(Workflow.PRODUCT_EXACT, "pastel", null, OutputFormat.STORY);
		assertThat(job.getSteps()).extracting(GenerationStep::getKind)
				.containsExactly(StepKind.CUTOUT, StepKind.BACKDROP);

		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		String url = done.outputUrlList().getFirst();
		assertThat(url).startsWith("/files/").endsWith(".jpg");

		BufferedImage output = ImageIO.read(storage.root().resolve(url.substring("/files/".length())).toFile());
		assertThat(output.getWidth()).isEqualTo(1080);
		assertThat(output.getHeight()).isEqualTo(1920);
		assertThat(credits()).isEqualTo(10 - Workflow.PRODUCT_EXACT.getCredits());
	}

	@Test
	void compositeJobDefaultsToSquareFormat() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);

		GenerationJob job = create(Workflow.PRODUCT_EXACT, null, null, null);

		assertThat(job.getFormat()).isEqualTo(OutputFormat.SQUARE);
	}

	@Test
	void compositeFailureRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(Result.ofUrls(List.of("data:image/png;base64,bm90LWFuLWltYWdl")));

		GenerationJob job = create(Workflow.PRODUCT_EXACT, null, null, null);
		service.pollRunningJobs();

		assertThat(service.get(accountId, job.getId()).getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void staleJobCopyCannotRefundASecondTime() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		GenerationJob job = create(Workflow.PRODUCT_PHOTO, null, null, null);
		GenerationJob stale = jobs.findById(job.getId()).orElseThrow();
		GenerationJob fresh = jobs.findById(job.getId()).orElseThrow();

		fresh.fail("timeout");
		service.persist(fresh);
		stale.fail("timeout");

		assertThatThrownBy(() -> service.persist(stale)).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void concurrentPollersRefundOnlyOnce() throws Exception {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenAnswer(invocation -> {
			Thread.sleep(300);
			return Status.COMPLETED;
		});
		when(aiProvider.result(any())).thenThrow(new RuntimeException("422 content policy"));
		GenerationJob job = create(Workflow.PRODUCT_PHOTO, null, null, null);

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			Future<?> first = pool.submit(service::pollRunningJobs);
			Future<?> second = pool.submit(service::pollRunningJobs);
			first.get();
			second.get();
		}

		assertThat(service.get(accountId, job.getId()).getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void retryReusesStoredInputsAndChargesAgain() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(Result.ofUrls(List.of("https://cdn/out.jpg")));
		GenerationJob job = create(Workflow.PRODUCT_PHOTO, "lifestyle", "trên bàn gỗ", null);
		service.pollRunningJobs();
		service.pollRunningJobs();
		assertThat(job.inputs()).containsOnlyKeys("product");

		GenerationJob again = service.retry(accountId, job.getId());

		assertThat(again.getId()).isNotEqualTo(job.getId());
		assertThat(again.getWorkflow()).isEqualTo(Workflow.PRODUCT_PHOTO);
		assertThat(again.getStyle()).isEqualTo("lifestyle");
		assertThat(again.getPrompt()).isEqualTo("trên bàn gỗ");
		assertThat(again.inputs()).isEqualTo(job.inputs());
		assertThat(credits()).isEqualTo(10 - 2 * Workflow.PRODUCT_PHOTO.getCredits());
		verify(aiProvider, times(2)).submit(anyString(), argThat(input ->
				TestImages.redProductDataUrl().equals(input.get("image_urls") instanceof List<?> urls ? urls.getFirst() : null)));
	}

	@Test
	void retryRejectsRunningJobAndOtherAccounts() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		GenerationJob job = create(Workflow.PRODUCT_PHOTO, null, null, null);
		Long otherAccount = accounts.save(new Account("other-" + System.nanoTime() + "@test.vn", "{noop}unused", "Other", 10)).getId();

		assertThatThrownBy(() -> service.retry(accountId, job.getId()))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.CONFLICT);
		assertThatThrownBy(() -> service.retry(otherAccount, job.getId()))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(credits()).isEqualTo(10 - Workflow.PRODUCT_PHOTO.getCredits());
	}

	@Test
	void resultsCanBeFilteredByWorkflowAndStatus() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		GenerationJob photo = create(Workflow.PRODUCT_PHOTO, null, null, null);
		GenerationJob failedPhoto = create(Workflow.PRODUCT_PHOTO, null, null, null);
		GenerationJob exact = create(Workflow.PRODUCT_EXACT, null, null, null);
		failedPhoto.fail("test");
		jobs.saveAndFlush(failedPhoto);

		assertThat(service.page(accountId, List.of(), null, 0, 10).getContent()).hasSize(3);
		assertThat(service.page(accountId, List.of(Workflow.PRODUCT_PHOTO, Workflow.PRODUCT_VIDEO), null, 0, 10)
				.getContent()).extracting(GenerationJob::getId).containsExactly(failedPhoto.getId(), photo.getId());
		assertThat(service.page(accountId, List.of(Workflow.PRODUCT_PHOTO), GenerationStatus.RUNNING, 0, 10)
				.getContent()).extracting(GenerationJob::getId).containsExactly(photo.getId());
		assertThat(service.page(accountId, null, GenerationStatus.RUNNING, 0, 10).getContent())
				.extracting(GenerationJob::getId).containsExactlyInAnyOrder(photo.getId(), exact.getId());
	}

	@Test
	void configuredHostsAllowFalCdnSubdomains() {
		assertThat(remoteFiles.isAllowed("https://v3b.fal.media/files/b/0aae3cf0/out.jpg")).isTrue();
		assertThat(remoteFiles.isAllowed("https://fal.media/files/out.jpg")).isTrue();
		assertThat(remoteFiles.isAllowed("https://example.com/out.jpg")).isFalse();
	}

	private GenerationJob create(Workflow workflow, String style, String prompt, OutputFormat format) {
		return service.create(accountId, new GenerationSpec(workflow, style, prompt, format, null, null, 1), PRODUCT);
	}

	private int credits() {
		return accounts.findById(accountId).orElseThrow().getCredits();
	}
}
