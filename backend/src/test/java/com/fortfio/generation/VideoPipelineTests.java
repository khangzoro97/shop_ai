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
import com.fortfio.ai.StepKind;
import com.fortfio.ai.VideoOptions;
import com.fortfio.ai.VideoResolution;
import com.fortfio.ai.Workflow;
import com.fortfio.ai.WorkflowInputBuilder;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;

/** Product videos: aspect ratio, length beyond one clip, resolution and background music. */
@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage"
})
class VideoPipelineTests {

	private static final String STORY_PHOTO = pngDataUrl(90, 160);
	private static final String SQUARE_PHOTO = pngDataUrl(100, 100);

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
	private String videoModel;
	private AppProperties.Video helpers;

	@BeforeEach
	void setUp() {
		accountId = accounts.save(new Account("video-" + System.nanoTime() + "@test.vn", "{noop}unused", "Video Shop", 400))
				.getId();
		videoModel = properties.fal().modelFor(Workflow.PRODUCT_VIDEO);
		helpers = properties.fal().video();
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
	}

	@Test
	void shortSilentVideoFromAStoryPhotoIsASingleCall() {
		returns(videoModel, "clip", "https://cdn.test/clip.mp4");

		GenerationJob job = service.create(accountId, spec(5, VideoResolution.P720, null), Map.of("product", STORY_PHOTO));

		assertThat(job.getCreditsCharged()).isEqualTo(20);
		assertThat(job.getFormat()).isEqualTo(OutputFormat.STORY);
		assertThat(submitted(videoModel))
				.containsEntry("duration", 5)
				.containsEntry("resolution", "720p")
				.containsEntry("audio", false)
				.containsEntry("image_url", STORY_PHOTO);

		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.MAIN);
		assertThat(done.outputUrlList()).containsExactly("https://cdn.test/clip.mp4");
	}

	@Test
	void photoInAnotherAspectRatioIsWidenedFirst() {
		returns(helpers.expandModel(), "expand", "https://cdn.test/wide.png");
		returns(videoModel, "clip", "https://cdn.test/clip.mp4");

		GenerationJob job = service.create(accountId, spec(5, VideoResolution.P360, null), Map.of("product", SQUARE_PHOTO));

		assertThat(job.getCreditsCharged()).isEqualTo(10);
		assertThat(submitted(helpers.expandModel())).containsEntry("aspect_ratio", "9:16");

		service.pollRunningJobs();
		assertThat(submitted(videoModel))
				.containsEntry("image_url", "https://cdn.test/wide.png")
				.containsEntry("resolution", "360p");
		service.pollRunningJobs();

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind).containsExactly(StepKind.EXPAND, StepKind.MAIN);
	}

	@Test
	void longVideoChainsClipsFromTheLastFrameThenAddsMusic() {
		when(aiProvider.submit(eq(videoModel), anyMap())).thenReturn(submission("clip1"), submission("clip2"));
		when(aiProvider.result(submission("clip1"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip1.mp4")));
		when(aiProvider.result(submission("clip2"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip2.mp4")));
		returns(helpers.musicModel(), "music", "https://cdn.test/music.wav");
		returns(helpers.lastFrameModel(), "frame", "https://cdn.test/frame.jpg");
		returns(helpers.joinModel(), "join", "https://cdn.test/joined.mp4");
		returns(helpers.addMusicModel(), "final", "https://cdn.test/final.mp4");

		GenerationJob job = service.create(accountId, spec(30, VideoResolution.P1080, MusicMood.LUXURY),
				Map.of("product", STORY_PHOTO));

		assertThat(job.getCreditsCharged()).isEqualTo(30 * 6 + VideoOptions.MUSIC_CREDITS);
		assertThat(submitted(videoModel)).containsEntry("duration", 15).containsEntry("resolution", "1080p");
		assertThat(submitted(helpers.musicModel())).containsEntry("duration", 31);
		assertThat(submitted(helpers.musicModel()).get("prompt").toString()).contains("no vocals");

		for (int i = 0; i < 5; i++) {
			service.pollRunningJobs();
		}
		assertThat(submitted(helpers.lastFrameModel()))
				.containsEntry("video_url", "https://cdn.test/clip1.mp4")
				.containsEntry("frame_type", "last");
		assertThat(submitted(videoModel)).containsEntry("image_url", "https://cdn.test/frame.jpg");
		assertThat(submitted(helpers.joinModel()))
				.containsEntry("video_urls", List.of("https://cdn.test/clip1.mp4", "https://cdn.test/clip2.mp4"));
		assertThat(submitted(helpers.addMusicModel()))
				.containsEntry("video_url", "https://cdn.test/joined.mp4")
				.containsEntry("audio_url", "https://cdn.test/music.wav");

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind).containsExactly(
				StepKind.CLIP, StepKind.MUSIC, StepKind.LAST_FRAME, StepKind.CLIP, StepKind.JOIN, StepKind.MAIN);
		assertThat(done.outputUrlList()).containsExactly("https://cdn.test/final.mp4");
		assertThat(done.video()).isEqualTo(new VideoOptions(30, VideoResolution.P1080, MusicMood.LUXURY));
	}

	@Test
	void silentLongVideoEndsWithTheJoin() {
		when(aiProvider.submit(eq(videoModel), anyMap())).thenReturn(submission("clip1"), submission("clip2"));
		when(aiProvider.result(submission("clip1"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip1.mp4")));
		when(aiProvider.result(submission("clip2"))).thenReturn(Result.ofUrls(List.of("https://cdn.test/clip2.mp4")));
		returns(helpers.lastFrameModel(), "frame", "https://cdn.test/frame.jpg");
		returns(helpers.joinModel(), "join", "https://cdn.test/joined.mp4");

		GenerationJob job = service.create(accountId, spec(20, VideoResolution.P720, null), Map.of("product", STORY_PHOTO));
		for (int i = 0; i < 4; i++) {
			service.pollRunningJobs();
		}

		GenerationJob done = service.get(accountId, job.getId());
		assertThat(done.getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
		assertThat(done.getSteps()).extracting(GenerationStep::getKind).containsExactly(
				StepKind.CLIP, StepKind.LAST_FRAME, StepKind.CLIP, StepKind.MAIN);
		assertThat(done.outputUrlList()).containsExactly("https://cdn.test/joined.mp4");
	}

	@Test
	void customLengthsAreSplitIntoNearEqualClips() {
		assertThat(inputBuilder.clipSeconds(spec(16, VideoResolution.P720, null))).containsExactly(16);
		assertThat(inputBuilder.clipSeconds(spec(17, VideoResolution.P720, null))).containsExactly(9, 8);
		assertThat(inputBuilder.clipSeconds(spec(30, VideoResolution.P720, null))).containsExactly(15, 15);
	}

	@Test
	void lengthOutsideTheSupportedRangeIsRejectedBeforeCharging() {
		assertThatThrownBy(() -> service.create(accountId, spec(3, VideoResolution.P720, null),
				Map.of("product", STORY_PHOTO)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Thời lượng video");
		assertThatThrownBy(() -> service.create(accountId, spec(31, VideoResolution.P720, null),
				Map.of("product", STORY_PHOTO)))
				.isInstanceOf(ApiException.class);
		assertThat(accounts.findById(accountId).orElseThrow().getCredits()).isEqualTo(400);
	}

	private void returns(String model, String id, String url) {
		when(aiProvider.submit(eq(model), anyMap())).thenReturn(submission(id));
		when(aiProvider.result(submission(id))).thenReturn(Result.ofUrls(List.of(url)));
	}

	private static GenerationSpec spec(int seconds, VideoResolution resolution, MusicMood music) {
		return new GenerationSpec(Workflow.PRODUCT_VIDEO, null, null, null, null, null, 1,
				new VideoOptions(seconds, resolution, music));
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

	private static String pngDataUrl(int width, int height) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
