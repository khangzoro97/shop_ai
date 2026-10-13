package com.fortfio.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.fortfio.ai.Workflow;

@ConfigurationProperties(prefix = "fortfio")
public record AppProperties(
		Cors cors,
		int signupCredits,
		Duration jobTimeout,
		Storage storage,
		Fal fal) {

	public record Cors(List<String> allowedOrigins) {
	}

	public record Storage(String localDir, String publicBaseUrl) {
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
}
