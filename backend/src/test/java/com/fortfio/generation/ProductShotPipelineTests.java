package com.fortfio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.Workflow;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.TestImages;

/** PRODUCT_EXACT through Bria product shot, and label checks for creative photos. */
@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage"
})
class ProductShotPipelineTests {

	private static final String PRODUCT = TestImages.redProductDataUrl();
	private static final String SCENE = TestImages.blueBackdropDataUrl();

	@Autowired
	private GenerationService service;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private AppProperties properties;

	@MockitoBean
	private AiProvider aiProvider;

	private Long accountId;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("shot-" + System.nanoTime() + "@test.vn", "{noop}unused", "Shot Shop", 20)).getId();
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
	}

	@Test
	void productShotRendersRequestedVariantsFromAutomaticCutout() {
		Submission cutout = submission("cutout");
		Submission shot = submission("shot");
		when(aiProvider.submit(eq(properties.fal().backgroundRemovalModel()), anyMap())).thenReturn(cutout);
		when(aiProvider.submit(eq(properties.fal().productShotModel()), anyMap())).thenReturn(shot);
		when(aiProvider.result(cutout)).thenReturn(Result.ofUrls(List.of("https://v3b.fal.media/cutout.png")));
		when(aiProvider.result(shot)).thenReturn(Result.ofUrls(List.of(PRODUCT, SCENE, PRODUCT)));

		GenerationJob job = service.create(accountId,
				spec(Workflow.PRODUCT_EXACT, OutputFormat.STORY, Placement.BOTTOM_LEFT, null, 2),
				Map.of("product", PRODUCT));

		assertThat(submittedInput(properties.fal().backgroundRemovalModel())).containsEntry("image_url", PRODUCT);
		assertThat(credits()).isEqualTo(20 - 2 * Workflow.PRODUCT_EXACT.getCredits());

		service.pollRunningJobs();

		Map<String, Object> input = submittedInput(properties.fal().productShotModel());
		assertThat(input)
				.containsEntry("image_url", "https://v3b.fal.media/cutout.png")
				.containsEntry("manual_placement_selection", "bottom_left")
				.containsEntry("shot_size", List.of(1080, 1920))
				.containsEntry("num_results", 2)
				.containsKey("scene_description")
				.doesNotContainKey("ref_image_url");

		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).hasSize(2).allMatch(url -> url.startsWith("/files/"));
		assertThat(done.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.CUTOUT, StepKind.MAIN);
	}

	@Test
	void namedProductIsCutOutFirstThenPlacedInReferenceScene() {
		Submission cutout = submission("cutout");
		Submission shot = submission("shot");
		when(aiProvider.submit(eq(properties.fal().guidedCutoutModel()), anyMap())).thenReturn(cutout);
		when(aiProvider.submit(eq(properties.fal().productShotModel()), anyMap())).thenReturn(shot);
		when(aiProvider.result(cutout)).thenReturn(Result.ofUrls(List.of("https://v3b.fal.media/cutout.png")));
		when(aiProvider.result(shot)).thenReturn(Result.ofUrls(List.of(PRODUCT)));

		GenerationJob job = service.create(accountId,
				spec(Workflow.PRODUCT_EXACT, null, null, "  chai serum trắng ", 1),
				Map.of("product", PRODUCT, "scene", SCENE));

		assertThat(submittedInput(properties.fal().guidedCutoutModel()).get("prompt").toString())
				.startsWith("Keep only the chai serum trắng.");
		assertThat(job.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.CUTOUT);

		service.pollRunningJobs();

		Map<String, Object> input = submittedInput(properties.fal().productShotModel());
		assertThat(input)
				.containsEntry("image_url", "https://v3b.fal.media/cutout.png")
				.containsEntry("ref_image_url", SCENE)
				.containsEntry("manual_placement_selection", "bottom_center")
				.containsEntry("shot_size", List.of(1200, 1200))
				.doesNotContainKey("scene_description");

		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getProductName()).isEqualTo("chai serum trắng");
		assertThat(done.inputs()).containsOnlyKeys("product", "scene");
	}

	@Test
	void creativePhotoWarnsWhenLabelTextChanged() {
		Submission photo = submission("photo");
		Submission ocr = submission("ocr");
		when(aiProvider.submit(eq(properties.fal().modelFor(Workflow.PRODUCT_PHOTO)), anyMap())).thenReturn(photo);
		when(aiProvider.submit(eq(properties.fal().ocrModel()), anyMap())).thenReturn(ocr);
		when(aiProvider.result(photo)).thenReturn(Result.ofUrls(List.of("https://cdn/a.jpg", "https://cdn/b.jpg")));
		when(aiProvider.result(ocr)).thenReturn(
				new Result(List.of(), "Nécessaire The Body Lotion FRAGRANCE-FREE 200 ml"),
				new Result(List.of(), "Nécessaire The Body Lotion FRAGRANCE-FREE 200 ml"),
				new Result(List.of(), "Necessaire The Body Lotion FRAGANCE FREE 340 ml"));

		GenerationJob job = service.create(accountId, spec(Workflow.PRODUCT_PHOTO, null, null, null, 2),
				Map.of("product", PRODUCT));
		service.pollRunningJobs();
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).containsExactly("https://cdn/a.jpg", "https://cdn/b.jpg");
		assertThat(done.getQualityWarning())
				.startsWith("Phương án 2:")
				.contains("FRAGRANCE", "200")
				.doesNotContain("LOTION");
	}

	@Test
	void labelCheckFailureDoesNotFailTheJob() {
		Submission photo = submission("photo");
		Submission ocr = submission("ocr");
		when(aiProvider.submit(eq(properties.fal().modelFor(Workflow.PRODUCT_PHOTO)), anyMap())).thenReturn(photo);
		when(aiProvider.submit(eq(properties.fal().ocrModel()), anyMap())).thenReturn(ocr);
		when(aiProvider.result(photo)).thenReturn(Result.ofUrls(List.of("https://cdn/a.jpg")));
		when(aiProvider.result(ocr)).thenThrow(new RuntimeException("ocr down"));

		GenerationJob job = service.create(accountId, spec(Workflow.PRODUCT_PHOTO, null, null, null, 1),
				Map.of("product", PRODUCT));
		service.pollRunningJobs();
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getQualityWarning()).isNull();
	}

	@Test
	void rejectsMoreVariantsThanTheWorkflowAllows() {
		assertThatThrownBy(() -> service.create(accountId, spec(Workflow.PRODUCT_HOLDING, null, null, null, 2),
				Map.of("person", PRODUCT, "product", PRODUCT)))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(credits()).isEqualTo(20);
	}

	private static GenerationSpec spec(Workflow workflow, OutputFormat format, Placement placement, String productName,
			int variants) {
		return new GenerationSpec(workflow, null, null, format, placement, productName, variants);
	}

	private static Submission submission(String id) {
		return new Submission(id, "https://queue/" + id + "/status", "https://queue/" + id);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> submittedInput(String model) {
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(aiProvider, org.mockito.Mockito.atLeastOnce()).submit(eq(model), captor.capture());
		return captor.getValue();
	}

	private int credits() {
		return accounts.findById(accountId).orElseThrow().getCredits();
	}
}
