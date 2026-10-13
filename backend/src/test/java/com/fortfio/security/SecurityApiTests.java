package com.fortfio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;

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
import com.fortfio.ai.AiProvider.Submission;
import com.fortfio.media.TestImages;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
		"fortfio.poll-interval-ms=3600000",
		"fortfio.rate-limit.auth-per-minute=1000",
		"fortfio.rate-limit.generations-per-minute=2",
		"fortfio.uploads.max-request-size=1MB",
		"fortfio.admin.email=Owner@Fortfio.test",
		"fortfio.admin.password=owner-secret-123"
})
@AutoConfigureMockMvc
class SecurityApiTests {

	private static final String PASSWORD = "matkhau123";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private AiProvider aiProvider;

	@Test
	void publicEndpointsWorkWithoutLogin() throws Exception {
		mvc.perform(get("/api/workflows")).andExpect(status().isOk());
		mvc.perform(get("/api/portfolio")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void privateEndpointsRequireLogin() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/generations")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorOnlyExposesHealth() throws Exception {
		String admin = login("owner@fortfio.test", "owner-secret-123");
		mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());
	}

	@Test
	void registerStartsWithoutCreditsAndLoginReturnsSameAccount() throws Exception {
		String email = uniqueEmail();
		String token = register(email);

		mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.credits").value(0))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.phoneVerified").value(false));

		postJson("/api/auth/register", null, registerBody(email.toUpperCase())).andExpect(status().isConflict());
		postJson("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}")
				.andExpect(status().isUnauthorized());
		assertThat(login(email, PASSWORD)).isNotBlank();
	}

	@Test
	void registerRejectsShortPassword() throws Exception {
		postJson("/api/auth/register", null,
				"{\"email\":\"" + uniqueEmail() + "\",\"password\":\"123\",\"shopName\":\"Shop\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Mật khẩu cần từ 8 đến 72 ký tự"));
	}

	@Test
	void verifiedPhoneGrantsSignupCreditsOnlyOnce() throws Exception {
		String token = register(uniqueEmail());
		String phone = uniquePhone();
		String code = sendOtp(token, phone);

		verify(token, phone, code.equals("000000") ? "111111" : "000000").andExpect(status().isBadRequest());
		verify(token, phone, code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.credits").value(30))
				.andExpect(jsonPath("$.phoneVerified").value(true));

		postJson("/api/me/phone/otp", token, "{\"phone\":\"" + phone + "\"}").andExpect(status().isConflict());

		String other = register(uniqueEmail());
		postJson("/api/me/phone/otp", other, "{\"phone\":\"+84" + phone.substring(1) + "\"}")
				.andExpect(status().isConflict());
	}

	@Test
	void otpIsInvalidatedAfterTooManyWrongCodes() throws Exception {
		String token = register(uniqueEmail());
		String phone = uniquePhone();
		String code = sendOtp(token, phone);
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 4; i++) {
			verify(token, phone, wrong).andExpect(status().isBadRequest());
		}
		verify(token, phone, wrong).andExpect(status().isTooManyRequests());
		verify(token, phone, code).andExpect(status().isBadRequest());
	}

	@Test
	void otpCannotBeResentDuringCooldown() throws Exception {
		String token = register(uniqueEmail());
		String phone = uniquePhone();
		sendOtp(token, phone);
		postJson("/api/me/phone/otp", token, "{\"phone\":\"" + phone + "\"}").andExpect(status().isTooManyRequests());
	}

	@Test
	void adminEndpointsRequireAdminRole() throws Exception {
		String customer = register(uniqueEmail());
		mvc.perform(get("/api/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
				.andExpect(status().isForbidden());

		String admin = login("owner@fortfio.test", "owner-secret-123");
		mvc.perform(get("/api/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.email == 'owner@fortfio.test')].role").value("ADMIN"));
	}

	@Test
	void shopsCannotSeeEachOthersJobs() throws Exception {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(new Submission("r", "s", "o"));
		String owner = verifiedShop();
		String jobId = JsonPath.read(createGeneration(owner, TestImages.redProductDataUrl())
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");

		String stranger = register(uniqueEmail());
		mvc.perform(get("/api/generations/" + jobId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/generations").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.totalItems").value(0));
		mvc.perform(get("/api/generations").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(jsonPath("$.items[0].id").value(jobId))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void generationsAreRateLimitedPerShop() throws Exception {
		when(aiProvider.submit(anyString(), anyMap())).thenReturn(new Submission("r", "s", "o"));
		String token = verifiedShop();
		createGeneration(token, TestImages.redProductDataUrl()).andExpect(status().isAccepted());
		createGeneration(token, TestImages.redProductDataUrl()).andExpect(status().isAccepted());
		createGeneration(token, TestImages.redProductDataUrl()).andExpect(status().isTooManyRequests());
	}

	@Test
	void rejectsLinksAndFilesThatAreNotImages() throws Exception {
		String token = verifiedShop();
		createGeneration(token, "https://169.254.169.254/latest/meta-data").andExpect(status().isBadRequest());
		createGeneration(token, "data:image/png;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Ảnh sản phẩm không phải file ảnh hợp lệ"));
	}

	@Test
	void rejectsOversizedRequestBody() throws Exception {
		String token = register(uniqueEmail());
		String huge = "data:image/png;base64," + "A".repeat(1_200_000);
		createGeneration(token, huge).andExpect(status().isContentTooLarge());
	}

	private String verifiedShop() throws Exception {
		String token = register(uniqueEmail());
		String phone = uniquePhone();
		verify(token, phone, sendOtp(token, phone)).andExpect(status().isOk());
		return token;
	}

	private ResultActions createGeneration(String token, String image) throws Exception {
		return postJson("/api/generations", token,
				"{\"workflow\":\"PRODUCT_PHOTO\",\"images\":{\"product\":\"" + image + "\"}}");
	}

	private String register(String email) throws Exception {
		String body = postJson("/api/auth/register", null, registerBody(email))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String login(String email, String password) throws Exception {
		String body = postJson("/api/auth/login", null,
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String sendOtp(String token, String phone) throws Exception {
		String body = postJson("/api/me/phone/otp", token, "{\"phone\":\"" + phone + "\"}")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.devCode");
	}

	private ResultActions verify(String token, String phone, String code) throws Exception {
		return postJson("/api/me/phone/verify", token, "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}");
	}

	private ResultActions postJson(String path, String token, String json) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, bearer(token));
		}
		return mvc.perform(request);
	}

	private static String registerBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"shopName\":\"Shop Test\"}";
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String uniqueEmail() {
		return "shop-" + System.nanoTime() + "@test.vn";
	}

	private static String uniquePhone() {
		return "09" + "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
	}
}
