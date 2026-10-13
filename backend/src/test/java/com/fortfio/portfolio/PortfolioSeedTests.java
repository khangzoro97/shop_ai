package com.fortfio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fortfio.poll-interval-ms=3600000")
class PortfolioSeedTests {

	@Autowired
	private PortfolioController controller;

	@Test
	void seededStockImagesAreNeverPresentedAsClientWork() {
		assertThat(controller.list(null)).isNotEmpty().allSatisfy(item -> {
			assertThat(item.sample()).isTrue();
			assertThat(item.client()).isEqualTo("Ảnh minh họa");
			assertThat(item.beforeUrl()).isNull();
		});
	}
}
