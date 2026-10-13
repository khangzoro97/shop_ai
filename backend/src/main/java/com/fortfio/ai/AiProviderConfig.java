package com.fortfio.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fortfio.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AiProviderConfig {

	@Bean
	AiProvider aiProvider(AppProperties properties) {
		AppProperties.Fal fal = properties.fal();
		if (!fal.enabled()) {
			log.warn("FAL_KEY is not set: using MockAiProvider, generations return sample media.");
			return new MockAiProvider();
		}
		return new FalAiProvider(fal.apiKey(), fal.queueUrl());
	}
}
