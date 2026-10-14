package com.fortfio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.ai.AiProvider;
import com.fortfio.ai.AiProvider.Result;
import com.fortfio.ai.AiProvider.Status;
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.MusicMood;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.PromptPlan;
import com.fortfio.ai.PromptWriter;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.VideoOptions;
import com.fortfio.ai.VideoResolution;
import com.fortfio.ai.Workflow;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.media.TestImages;

/** Simple mode: the customer's description is turned into prompts by a language model. */
@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage"
})
class SimpleModeTests {

	private static final String PRODUCT = TestImages.redProductDataUrl();
	private static final String BRIEF = "serum trên bàn đá, có hoa, đặt lệch phải";
	private static final String IMAGE_PLAN = """
			```json
			{"productName": "white serum bottle", "placement": "bottom_right", "music": null,
			 "summary": "Chai serum trên bàn đá cẩm thạch, hoa hồng phía sau.",
			 "scene": "White marble table, pastel roses softly blurred behind, morning sunlight."}
			```""";

	@Autowired
	private GenerationService service;

	@Autowired
	private PromptWriter promptWriter;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private AppProperties properties;

	@MockitoBean
	private AiProvider aiProvider;

	private Long accountId;
	private String llm;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("simple-" + System.nanoTime() + "@test.vn", "{noop}unused", "Simple Shop", 400))
				.getId();
		llm = properties.fal().llm().visionEndpoint();
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
	}

	@Test
	void descriptionIsPlannedFirstThenDrivesCutoutPlacementAndScene() {
		returnsText(llm, "plan", IMAGE_PLAN);
		returns(properties.fal().guidedCutoutModel(), "cutout", "https://v3b.fal.media/cutout.png");
		returns(properties.fal().productShotModel(), "shot", PRODUCT);

		GenerationJob job = service.create(accountId, simple(Workflow.PRODUCT_EXACT, BRIEF, null),
				Map.of("product", PRODUCT));

		assertThat(job.getCreditsCharged()).isEqualTo(Workflow.PRODUCT_EXACT.getCredits());
		assertThat(job.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.PROMPT);
		Map<String, Object> planInput = submitted(llm);
		assertThat(planInput)
				.containsEntry("model", properties.fal().llm().model())
				.containsEntry("image_urls", List.of(PRODUCT));
		assertThat(planInput.get("prompt").toString()).contains(BRIEF);
		assertThat(planInput.get("system_prompt").toString()).contains("placement").doesNotContain("clips");

		service.pollRunningJobs();
		assertThat(submitted(properties.fal().guidedCutoutModel()).get("prompt").toString())
				.contains("Keep only the white serum bottle");
		assertThat(service.get(accountId, job.getId()).planSummary()).startsWith("Chai serum trên bàn đá");

		service.pollRunningJobs();
		assertThat(submitted(properties.fal().productShotModel()))
				.containsEntry("scene_description",
						"White marble table, pastel roses softly blurred behind, morning sunlight.")
				.containsEntry("manual_placement_selection", "bottom_right");

		service.pollRunningJobs();
		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind)
				.containsExactly(StepKind.PROMPT, StepKind.CUTOUT, StepKind.MAIN);
	}

	@Test
	void unusableAnswerFallsBackToTheDefaultSceneAndTheRawDescription() {
		returnsText(llm, "plan", "Sorry, I cannot help with that.");
		returns(properties.fal().backgroundRemovalModel(), "cutout", "https://v3b.fal.media/cutout.png");
		returns(properties.fal().productShotModel(), "shot", PRODUCT);

		GenerationJob job = service.create(accountId, simple(Workflow.PRODUCT_EXACT, BRIEF, null),
				Map.of("product", PRODUCT));
		service.pollRunningJobs();
		service.pollRunningJobs();

		String scene = submitted(properties.fal().productShotModel()).get("scene_description").toString();
		assertThat(scene)
				.startsWith(Workflow.PRODUCT_EXACT.getStyles().getFirst().prompt())
				.endsWith(BRIEF);
		assertThat(service.get(accountId, job.getId()).plan()).isEqualTo(PromptPlan.EMPTY);
	}

	@Test
	void videoClipsFollowTheScriptAndTheAiPicksTheMusicMood() {
		String videoModel = properties.fal().modelFor(Workflow.PRODUCT_VIDEO);
		AppProperties.Video helpers = properties.fal().video();
		returnsText(llm, "plan", """
				{"productName": "serum", "music": "LUXURY", "summary": "Video sang trọng hai cảnh.",
				 "clips": [{"prompt": "Slow push-in on the bottle."}, "Camera orbits slightly, petals fall."]}""");
		when(aiProvider.submit(eq(videoModel), anyMap())).thenReturn(submission("clip1"), submission("clip2"));
		when(aiProvider.result(submission("clip1"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip1.mp4")));
		when(aiProvider.result(submission("clip2"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip2.mp4")));
		returns(helpers.musicModel(), "music", "https://cdn.test/music.wav");
		returns(helpers.lastFrameModel(), "frame", "https://cdn.test/frame.jpg");
		returns(helpers.joinModel(), "join", "https://cdn.test/joined.mp4");
		returns(helpers.addMusicModel(), "final", "https://cdn.test/final.mp4");

		GenerationJob job = service.create(accountId, simple(Workflow.PRODUCT_VIDEO, "",
				new VideoOptions(30, VideoResolution.P720, MusicMood.UPBEAT)), Map.of("product", storyPhoto()));

		assertThat(job.getCreditsCharged()).isEqualTo(30 * 4 + VideoOptions.MUSIC_CREDITS);
		assertThat(submitted(llm).get("system_prompt").toString()).contains("exactly 2", "15, 15");
		assertThat(submitted(llm).get("prompt").toString()).contains("(empty)");

		service.pollRunningJobs();
		assertThat(submitted(helpers.musicModel()).get("prompt").toString())
				.startsWith(MusicMood.LUXURY.getPrompt().substring(0, 20));
		assertThat(submitted(videoModel).get("prompt").toString()).startsWith("Slow push-in on the bottle.");

		for (int i = 0; i < 5; i++) {
			service.pollRunningJobs();
		}
		assertThat(submitted(videoModel).get("prompt").toString()).startsWith("Camera orbits slightly");
		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.outputUrlList()).containsExactly("https://cdn.test/final.mp4");
		assertThat(done.video().music()).isEqualTo(MusicMood.LUXURY);
	}

	@Test
	void retryReusesTheStoredPlan() {
		returnsText(llm, "plan", IMAGE_PLAN);
		returns(properties.fal().guidedCutoutModel(), "cutout", "https://v3b.fal.media/cutout.png");
		returns(properties.fal().productShotModel(), "shot", PRODUCT);
		GenerationJob job = service.create(accountId, simple(Workflow.PRODUCT_EXACT, BRIEF, null),
				Map.of("product", PRODUCT));
		for (int i = 0; i < 3; i++) {
			service.pollRunningJobs();
		}

		GenerationJob again = service.retry(accountId, job.getId());

		verify(aiProvider, times(1)).submit(eq(llm), anyMap());
		assertThat(again.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.CUTOUT);
		assertThat(again.getBrief()).isEqualTo(BRIEF);
		assertThat(again.planSummary()).startsWith("Chai serum trên bàn đá");
	}

	@Test
	void workflowsWithoutPromptsIgnoreTheDescription() {
		GenerationSpec spec = new GenerationSpec(Workflow.VIRTUAL_TRYON, null, null, null, null, null, 1, null,
				"áo thun trắng", null);
		returns(properties.fal().modelFor(Workflow.VIRTUAL_TRYON), "tryon", PRODUCT);

		GenerationJob job = service.create(accountId, spec, Map.of("person", PRODUCT, "garment", PRODUCT));

		assertThat(job.getBrief()).isNull();
		assertThat(job.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.MAIN);
	}

	@Test
	void assistReturnsCleanVietnameseTextAndExplainsFailures() {
		when(aiProvider.run(eq(llm), anyMap()))
				.thenReturn(new Result(List.of(), "```\n\"Chai serum đặt trên mặt bàn đá cẩm thạch trắng.\"\n```"));

		String text = promptWriter.assist(Workflow.PRODUCT_VIDEO, "serum trên bàn đá", PRODUCT, List.of(10, 10));

		assertThat(text).isEqualTo("Chai serum đặt trên mặt bàn đá cẩm thạch trắng.");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(aiProvider).run(eq(llm), captor.capture());
		assertThat(captor.getValue().get("system_prompt").toString()).contains("20 giây", "Cảnh 2 (10–20 giây)");

		when(aiProvider.run(eq(properties.fal().llm().textEndpoint()), anyMap()))
				.thenThrow(new IllegalStateException("timeout"));
		assertThatThrownBy(() -> promptWriter.assist(Workflow.PRODUCT_EXACT, "serum", null, List.of()))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("AI chưa viết được");
	}

	@Test
	void planParsingToleratesFencesObjectClipsAndUnknownValues() {
		PromptPlan plan = PromptPlan.parse("Here you go:\n```json\n{\"placement\": \"middle\", \"music\": \"chill\","
				+ " \"clips\": [{\"prompt\": \"a\"}, \"b\", {\"other\": 1}], \"extra\": true}\n```");

		assertThat(plan.placement()).isNull();
		assertThat(plan.music()).isEqualTo(MusicMood.CHILL);
		assertThat(plan.clips()).containsExactly("a", "b");
		assertThat(plan.clip(1, 2)).isEqualTo("b");
		assertThat(plan.clip(0, 3)).isNull();
		assertThat(PromptPlan.parse("not json")).isEqualTo(PromptPlan.EMPTY);
		assertThat(PromptPlan.fromJson(plan.toJson())).isEqualTo(plan);
		assertThat(PromptPlan.parse(IMAGE_PLAN).placement()).isEqualTo(Placement.BOTTOM_RIGHT);
	}

	private static GenerationSpec simple(Workflow workflow, String brief, VideoOptions video) {
		return new GenerationSpec(workflow, null, null, workflow.isVideo() ? OutputFormat.STORY : null, null, null, 1,
				video, brief, null);
	}

	private void returns(String model, String id, String url) {
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission(id));
		when(aiProvider.result(submission(id))).thenReturn(Result.ofUrls(List.of(url)));
	}

	private void returnsText(String model, String id, String text) {
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission(id));
		when(aiProvider.result(submission(id))).thenReturn(new Result(List.of(), text));
	}

	private static Submission submission(String id) {
		return new Submission(id, "https://queue/" + id + "/status", "https://queue/" + id);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> submitted(String model) {
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(aiProvider, atLeastOnce()).submit(eq(model), captor.capture());
		return captor.getValue();
	}

	private static String storyPhoto() {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(90, 160, BufferedImage.TYPE_INT_RGB), "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
