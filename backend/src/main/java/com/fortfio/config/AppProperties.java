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
		Admin admin) {

	public record Cors(List<String> allowedOrigins) {
	}

	public record Storage(String type, String localDir, String publicBaseUrl, S3 s3) {
	}

	/** S3-compatible object storage; Cloudflare R2 uses the same API with a custom endpoint. */
	public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
	}

	public record Fal(
			String apiKey,
			String queueUrl,
			Map<Workflow, String> models,
			String backgroundRemovalModel,
			String backdropModel) {

		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
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
	 * @param jwtSecret HMAC key for login tokens; when blank a random key is generated, so
	 *                  tokens stop working after a restart
	 */
	public record Auth(String jwtSecret, Duration tokenTtl, Otp otp) {
	}

	/**
	 * @param exposeCode return the code in the API response; only for development, while no
	 *                   SMS or Zalo sender is configured
	 */
	public record Otp(Duration ttl, Duration resendCooldown, int maxAttempts, boolean exposeCode) {
	}

	public record RateLimit(int generationsPerMinute, int authPerMinute, int otpPerHour) {
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
