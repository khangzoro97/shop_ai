package com.fortfio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.AiProvider.Result;
import com.fortfio.ai.AiProvider.Status;
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.Workflow;
import com.fortfio.ai.WorkflowInputBuilder;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.TestImages;

/** Model holding a product, FASHN try-on, and relight/upscale of finished results. */
@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage"
})
class HoldingTryOnEnhanceTests {

	private static final String PRODUCT = TestImages.redProductDataUrl();
	private static final String PERSON = TestImages.blueBackdropDataUrl();

	@Autowired
	private GenerationService service;

	@Autowired
	private WorkflowInputBuilder inputBuilder;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private AppProperties properties;

	@MockitoBean
	private AiProvider aiProvider;

	private Long accountId;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("g2-" + System.nanoTime() + "@test.vn", "{noop}unused", "G2 Shop", 40)).getId();
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.submit(eq(properties.fal().ocrModel()), anyMap())).thenReturn(submission("ocr"));
		when(aiProvider.result(submission("ocr"))).thenReturn(new Result(List.of(), "LABEL 100ML"));
	}

	@Test
	void holdingPlacesTheProductInThePersonsHandAndChecksTheLabel() {
		String model = properties.fal().modelFor(Workflow.PRODUCT_HOLDING);
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission("hold"));
		when(aiProvider.result(submission("hold"))).thenReturn(Result.ofUrls(List.of(PERSON)));

		GenerationJob job = service.create(accountId, spec(Workflow.PRODUCT_HOLDING, "face", 1),
				Map.of("person", PERSON, "product", PRODUCT));

		assertThat(submittedInput(model))
				.containsEntry("person_image_url", PERSON)
				.containsEntry("product_image_urls", List.of(PRODUCT));
		assertThat(submittedInput(model).get("instruction").toString()).contains("next to the face");

		service.pollRunningJobs();
		assertThat(submittedInput(properties.fal().ocrModel())).containsKey("image_url");
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind)
				.containsExactly(StepKind.MAIN, StepKind.QC_SOURCE, StepKind.QC_RESULT);
	}

	@Test
	void unreadableLabelGetsItsOwnWarning() {
		String model = properties.fal().modelFor(Workflow.PRODUCT_HOLDING);
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission("hold"));
		when(aiProvider.result(submission("hold"))).thenReturn(Result.ofUrls(List.of(PERSON)));
		when(aiProvider.result(submission("ocr"))).thenReturn(
				new Result(List.of(), "Nécessaire The Body Lotion 200 ml"),
				new Result(List.of(), "a woman in a yellow hoodie"));

		GenerationJob job = service.create(accountId, spec(Workflow.PRODUCT_HOLDING, null, 1),
				Map.of("person", PERSON, "product", PRODUCT));
		service.pollRunningJobs();
		service.pollRunningJobs();

		assertThat(service.get(accountId, job.getId()).getQualityWarning())
				.startsWith("Không đọc được chữ trên nhãn")
				.doesNotContain("đổi chữ");
	}

	@Test
	void tryOnUsesFashnWithGarmentTypeAndVariants() {
		String model = properties.fal().modelFor(Workflow.VIRTUAL_TRYON);
		assertThat(model).contains("fashn");
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission("tryon"));
		when(aiProvider.result(submission("tryon"))).thenReturn(Result.ofUrls(List.of(PERSON, PRODUCT)));

		GenerationJob job = service.create(accountId, spec(Workflow.VIRTUAL_TRYON, "tops", 2),
				Map.of("person", PERSON, "garment", PRODUCT));

		assertThat(submittedInput(model))
				.containsEntry("model_image", PERSON)
				.containsEntry("garment_image", PRODUCT)
				.containsEntry("category", "tops")
				.containsEntry("num_samples", 2);
		assertThat(job.getCreditsCharged()).isEqualTo(2 * Workflow.VIRTUAL_TRYON.getCredits());

		service.pollRunningJobs();
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).hasSize(2);
	}

	@Test
	void relightRunsOnAChosenVariantOfAFinishedResult() {
		GenerationJob source = finishedTryOn();
		String model = properties.fal().modelFor(Workflow.RELIGHT);
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission("relight"));

		GenerationJob relit = service.enhance(accountId, source.getId(), Workflow.RELIGHT, "golden", 1);

		Map<String, Object> input = submittedInput(model);
		assertThat(input)
				.containsEntry("light_direction", "side")
				.containsEntry("light_type", "low-angle sunlight");
		assertThat(input.get("image_url").toString()).startsWith("data:image/");
		assertThat(relit.getCreditsCharged()).isEqualTo(Workflow.RELIGHT.getCredits());
		assertThat(relit.inputs()).containsOnlyKeys("image");
	}

	@Test
	void upscaleTargetsTheLongSideAndRefusesImagesAlreadyThatLarge() {
		Map<String, Object> input = inputBuilder
				.firstStage(spec(Workflow.UPSCALE_2K, null, 1), Map.of("image", PERSON)).getFirst().input();
		assertThat(input.get("upscale_factor")).isEqualTo(3.41);

		Map<String, Object> capped = inputBuilder
				.firstStage(spec(Workflow.UPSCALE_4K, null, 1), Map.of("image", PRODUCT)).getFirst().input();
		assertThat(capped.get("upscale_factor")).isEqualTo(4.0);

		assertThatThrownBy(() -> inputBuilder.firstStage(spec(Workflow.UPSCALE_2K, null, 1),
				Map.of("image", pngDataUrl(1800, 100))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("đã đạt độ phân giải");
	}

	@Test
	void enhancementsOnlyStartFromFinishedImages() {
		assertThatThrownBy(() -> service.create(accountId, spec(Workflow.UPSCALE_2K, null, 1), Map.of("image", PERSON)))
				.isInstanceOf(ApiException.class);

		when(aiProvider.submit(eq(properties.fal().modelFor(Workflow.VIRTUAL_TRYON)), anyMap()))
				.thenReturn(submission("running"));
		when(aiProvider.status(submission("running"))).thenReturn(Status.IN_PROGRESS);
		GenerationJob running = service.create(accountId, spec(Workflow.VIRTUAL_TRYON, null, 1),
				Map.of("person", PERSON, "garment", PRODUCT));

		assertThatThrownBy(() -> service.enhance(accountId, running.getId(), Workflow.UPSCALE_2K, null, 0))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.CONFLICT);
		assertThatThrownBy(() -> service.enhance(accountId, running.getId(), Workflow.PRODUCT_PHOTO, null, 0))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private GenerationJob finishedTryOn() {
		String model = properties.fal().modelFor(Workflow.VIRTUAL_TRYON);
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission("source"));
		when(aiProvider.result(submission("source"))).thenReturn(Result.ofUrls(List.of(PERSON, PRODUCT)));
		GenerationJob job = service.create(accountId, spec(Workflow.VIRTUAL_TRYON, null, 2),
				Map.of("person", PERSON, "garment", PRODUCT));
		service.pollRunningJobs();
		service.pollRunningJobs();
		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		return done;
	}

	private static GenerationSpec spec(Workflow workflow, String style, int variants) {
		return new GenerationSpec(workflow, style, null, null, null, null, variants);
	}

	private static Submission submission(String id) {
		return new Submission(id, "https://queue/" + id + "/status", "https://queue/" + id);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> submittedInput(String model) {
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(aiProvider, atLeastOnce()).submit(eq(model), captor.capture());
		return captor.getValue();
	}

	private static String pngDataUrl(int width, int height) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
