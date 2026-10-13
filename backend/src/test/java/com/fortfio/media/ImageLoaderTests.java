package com.fortfio.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ImageLoaderTests {

	private static final List<String> ALLOWED = List.of("fal.media", "*.fal.media");

	@Test
	void allowsProviderHostsOnly() {
		assertThat(ImageLoader.isAllowedHost("fal.media", ALLOWED)).isTrue();
		assertThat(ImageLoader.isAllowedHost("v3.FAL.media", ALLOWED)).isTrue();

		assertThat(ImageLoader.isAllowedHost("169.254.169.254", ALLOWED)).isFalse();
		assertThat(ImageLoader.isAllowedHost("localhost", ALLOWED)).isFalse();
		assertThat(ImageLoader.isAllowedHost("evilfal.media", ALLOWED)).isFalse();
		assertThat(ImageLoader.isAllowedHost("fal.media.evil.com", ALLOWED)).isFalse();
		assertThat(ImageLoader.isAllowedHost(null, ALLOWED)).isFalse();
	}
}
