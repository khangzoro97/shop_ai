package com.fortfio.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fortfio.ai.AiProvider;
import com.fortfio.ai.AiProvider.Result;
import com.fortfio.ai.AiProvider.Status;
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.generation.GenerationService;
import com.fortfio.generation.GenerationStatus;
import com.fortfio.media.TestImages;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.storage.local-dir=target/test-storage",
		"fortfio.fal.exact-engine=composite",
		"fortfio.rate-limit.auth-per-minute=1000",
		"fortfio.trial.per-ip-per-day=1"
})
@AutoConfigureMockMvc
class TrialApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private GenerationService generationService;

	@Autowired
	private TrialService trialService;

	@MockitoBean
	private AiProvider aiProvider;

	@BeforeEach
	void setUp() {
		when(aiProvider.submit(anyString(), anyMap()))
				.thenAnswer(i -> new Submission(UUID.randomUUID().toString(), "mock://status", "mock://result"));
		when(aiProvider.status(any())).thenReturn(Status.COMPLETED);
		when(aiProvider.result(any())).thenReturn(Result.ofUrls(List.of(TestImages.redProductDataUrl())));
	}

	@Test
	void visitorGetsWatermarkedPreviewThenClaimsOriginalAfterSignup() throws Exception {
		String ip = randomIp();
		String id = JsonPath.read(startTrial(ip).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString(), "$.id");
		finish(UUID.fromString(id));

		mvc.perform(get("/api/trial/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.previews[0]").value("/api/trial/" + id + "/preview/0"));
		byte[] preview = mvc.perform(get("/api/trial/" + id + "/preview/0"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(ImageIO.read(new ByteArrayInputStream(preview))).isNotNull();

		startTrial(ip).andExpect(status().isTooManyRequests());
		mvc.perform(post("/api/trial/" + id + "/claim")).andExpect(status().isUnauthorized());

		String token = register();
		mvc.perform(post("/api/trial/" + id + "/claim").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.outputUrls[0]").exists());
		mvc.perform(get("/api/generations/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
		mvc.perform(get("/api/trial/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void systemAccountCannotLogIn() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + TrialService.SYSTEM_EMAIL + "\",\"password\":\"anything-123\"}"))
				.andExpect(status().isUnauthorized());
	}

	private void finish(UUID id) {
		for (int i = 0; i < 6 && trialService.get(id).getStatus() == GenerationStatus.RUNNING; i++) {
			generationService.pollRunningJobs();
		}
		assertThat(trialService.get(id).getStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
	}

	private ResultActions startTrial(String ip) throws Exception {
		return mvc.perform(post("/api/trial")
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scene\":\"MARBLE\",\"image\":\"" + TestImages.redProductDataUrl() + "\"}"));
	}

	private String register() throws Exception {
		String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"trial-" + System.nanoTime() + "@test.vn\",\"password\":\"matkhau123\","
						+ "\"shopName\":\"Shop Thu\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private static String randomIp() {
		return "10.%d.%d.%d".formatted((int) (Math.random() * 250), (int) (Math.random() * 250),
				(int) (Math.random() * 250));
	}
}
