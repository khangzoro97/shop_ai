package com.fortfio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.AiProvider.Status;
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.Workflow;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.LocalFileStorage;
import com.fortfio.media.TestImages;

@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage"
})
class GenerationServiceTests {

	private static final Map<String, String> PRODUCT = Map.of("product", "https://example.com/serum.jpg");
	private static final Submission SUBMISSION = new Submission("req-1", "https://queue/status", "https://queue/result");

	@Autowired
	private GenerationService service;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private AppProperties properties;

	@Autowired
	private LocalFileStorage storage;

	@MockitoBean
	private AiProvider aiProvider;

	private Long accountId;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("shop-" + System.nanoTime() + "@test.vn", "Test Shop", 10)).getId();
	}

	@Test
	void successfulJobChargesCreditsAndStoresOutput() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(List.of("https://cdn/out.jpg"));

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_PHOTO, "lifestyle", null, null, PRODUCT);
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).containsExactly("https://cdn/out.jpg");
		assertThat(credits()).isEqualTo(10 - Workflow.PRODUCT_PHOTO.getCredits());
	}

	@Test
	void failedResultRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenThrow(new RuntimeException("422 content policy"));

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_PHOTO, null, null, null, PRODUCT);
		service.pollRunningJobs();

		assertThat(service.get(accountId, job.getId()).getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void submitFailureRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenThrow(new RuntimeException("timeout"));

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_PHOTO, null, null, null, PRODUCT);

		assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void rejectsWhenNotEnoughCredits() {
		assertThatThrownBy(() -> service.create(accountId, Workflow.PRODUCT_VIDEO, null, null, null, PRODUCT))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.PAYMENT_REQUIRED);
		assertThat(credits()).isEqualTo(10);
	}

	@Test
	void rejectsMissingImageBeforeCharging() {
		assertThatThrownBy(() -> service.create(accountId, Workflow.VIRTUAL_TRYON, null, null, null, PRODUCT))
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
		when(aiProvider.result(cutout)).thenReturn(List.of(TestImages.redProductDataUrl()));
		when(aiProvider.result(backdrop)).thenReturn(List.of(TestImages.blueBackdropDataUrl()));

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_EXACT, "pastel", null, OutputFormat.STORY, PRODUCT);
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

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_EXACT, null, null, null, PRODUCT);

		assertThat(job.getFormat()).isEqualTo(OutputFormat.SQUARE);
	}

	@Test
	void compositeFailureRefundsCredits() {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(SUBMISSION);
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(List.of("data:image/png;base64,bm90LWFuLWltYWdl"));

		GenerationJob job = service.create(accountId, Workflow.PRODUCT_EXACT, null, null, null, PRODUCT);
		service.pollRunningJobs();

		assertThat(service.get(accountId, job.getId()).getStatus()).isEqualTo(GenerationStatus.FAILED);
		assertThat(credits()).isEqualTo(10);
	}

	private int credits() {
		return accounts.findById(accountId).orElseThrow().getCredits();
	}
}
