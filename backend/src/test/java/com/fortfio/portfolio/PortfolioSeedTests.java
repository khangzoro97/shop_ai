package com.fortfio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fortfio.config.DataSeeder;

@SpringBootTest(properties = "fortfio.poll-interval-ms=3600000")
class PortfolioSeedTests {

	@Autowired
	private PortfolioController controller;

	@Test
	void seededDemosAreNeverPresentedAsClientWork() {
		assertThat(controller.list(null)).isNotEmpty().allSatisfy(item -> {
			assertThat(item.sample()).isTrue();
			assertThat(item.client()).isEqualTo(DataSeeder.SAMPLE_CLIENT);
			assertThat(item.afterUrl()).startsWith("/portfolio/");
		});
	}
}
