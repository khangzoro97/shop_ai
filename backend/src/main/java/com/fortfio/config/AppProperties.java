package com.fortfio.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import com.fortfio.ai.Workflow;

@ConfigurationProperties(prefix = "fortfio")
public record AppProperties(
		Cors cors,
		int signupCredits,
		Duration jobTimeout,
		Storage storage,
		Fal fal,
		Auth auth,
		RateLimit rateLimit,
		Uploads uploads,
		Admin admin,
		String appUrl,
		Mail mail,
		Trial trial) {

	/**
	 * Free try on the Home page without an account; the result is only shown watermarked.
	 *
	 * @param perIpPerDay trials per visitor IP in 24 hours
	 * @param maxPerDay   trials for all visitors per calendar day (Vietnam time), capping the AI cost
	 * @param previewSize longest side of the watermarked preview, in pixels
	 */
	public record Trial(boolean enabled, int perIpPerDay, int maxPerDay, int previewSize) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Storage(String type, String localDir, String publicBaseUrl, S3 s3) {
	}

	/** S3-compatible object storage; Cloudflare R2 uses the same API with a custom endpoint. */
	public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
	}

	/**
	 * @param exactEngine how {@link Workflow#PRODUCT_EXACT} places the product: {@code product-shot}
	 *                    (Bria, grounded with natural shadows) or {@code composite} (backdrop
	 *                    generated separately, then composited on our server)
	 */
	public record Fal(
			String apiKey,
			String queueUrl,
			String runUrl,
			Map<Workflow, String> models,
			String exactEngine,
			String productShotModel,
			String guidedCutoutModel,
			String backgroundRemovalModel,
			String backdropModel,
			String ocrModel,
			Video video,
			Llm llm) {

		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}

		public boolean compositeEngine() {
			return "composite".equalsIgnoreCase(exactEngine);
		}

		public String modelFor(Workflow workflow) {
			String model = models.get(workflow);
			if (model == null) {
				throw new IllegalStateException("No fal model configured for " + workflow);
			}
			return model;
		}
	}

	/**
	 * Helper models around the video model.
	 *
	 * @param expandModel    widens the first frame to the chosen aspect ratio
	 * @param lastFrameModel extracts the frame the next clip starts from
	 * @param joinModel      concatenates clips
	 * @param addMusicModel  lays the music under the video, cut to the video length
	 */
	public record Video(String expandModel, String musicModel, String lastFrameModel, String joinModel,
			String addMusicModel) {
	}

	/**
	 * Language model that turns the customer's own description into prompts (simple mode).
	 *
	 * @param visionEndpoint fal endpoint used when a product photo is sent along
	 * @param textEndpoint   fal endpoint used for text only
	 * @param model          model id behind fal's OpenRouter proxy
	 */
	public record Llm(String visionEndpoint, String textEndpoint, String model) {
	}

	/**
	 * @param jwtSecret HMAC key for login tokens; when blank a random key is generated, so
	 *                  tokens stop working after a restart
	 */
	public record Auth(String jwtSecret, Duration tokenTtl, Otp otp, Reset passwordReset, String googleClientId) {

		public boolean googleEnabled() {
			return googleClientId != null && !googleClientId.isBlank();
		}
	}

	/**
	 * @param ttl         how long the emailed link works
	 * @param perHour     reset emails per address and per IP
	 */
	public record Reset(Duration ttl, int perHour) {
	}

	/** Outgoing SMTP; reset links are only logged while the host is empty. */
	public record Mail(String host, int port, String username, String password, String from) {

		public boolean configured() {
			return host != null && !host.isBlank();
		}
	}

	/**
	 * @param exposeCode return the code in the API response; only for development, while no
	 *                   SMS or Zalo sender is configured
	 */
	public record Otp(Duration ttl, Duration resendCooldown, int maxAttempts, boolean exposeCode) {
	}

	public record RateLimit(int generationsPerMinute, int authPerMinute, int otpPerHour, int promptAssistsPerMinute,
			int inputChecksPerMinute) {
	}

	/**
	 * @param allowedRemoteHosts hosts the server may download images from (AI results);
	 *                           a leading "*." also matches subdomains
	 */
	public record Uploads(DataSize maxImageSize, DataSize maxRequestSize, DataSize maxDownloadSize,
			List<String> allowedRemoteHosts) {
	}

	public record Admin(String email, String password) {

		public boolean configured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}
	}
}
