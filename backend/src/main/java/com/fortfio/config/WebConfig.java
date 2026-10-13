package com.fortfio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fortfio.media.LocalFileStorage;

import lombok.RequiredArgsConstructor;

/** CORS is configured in SecurityConfig, so it also applies to requests rejected by security. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final LocalFileStorage localFileStorage;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = localFileStorage.root().toUri().toString();
		registry.addResourceHandler("/files/**")
				.addResourceLocations(location.endsWith("/") ? location : location + "/");
	}
}
