package com.fortfio.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RemoteFilesTests {

	private static final List<String> ALLOWED = List.of("fal.media", "*.fal.media");

	@Test
	void allowsProviderHostsOnly() {
		assertThat(RemoteFiles.isAllowedHost("fal.media", ALLOWED)).isTrue();
		assertThat(RemoteFiles.isAllowedHost("v3.FAL.media", ALLOWED)).isTrue();

		assertThat(RemoteFiles.isAllowedHost("169.254.169.254", ALLOWED)).isFalse();
		assertThat(RemoteFiles.isAllowedHost("localhost", ALLOWED)).isFalse();
		assertThat(RemoteFiles.isAllowedHost("evilfal.media", ALLOWED)).isFalse();
		assertThat(RemoteFiles.isAllowedHost("fal.media.evil.com", ALLOWED)).isFalse();
		assertThat(RemoteFiles.isAllowedHost(null, ALLOWED)).isFalse();
	}
}
