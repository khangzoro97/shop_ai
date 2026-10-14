package com.fortfio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fortfio.ai.Workflow.Style;

class WorkflowStylesTests {

	@Test
	void everyStyleHasUniqueIdAndShopFacingTexts() {
		for (Workflow workflow : Workflow.values()) {
			List<Style> styles = workflow.getStyles();
			assertThat(styles).extracting(Style::id).doesNotHaveDuplicates();
			assertThat(styles).allSatisfy(s -> assertThat(List.of(s.group(), s.label(), s.hint(), s.prompt()))
					.allMatch(text -> !text.isBlank()));
		}
	}

	@Test
	void stylesStoredOnOlderJobsStillResolve() {
		for (String id : List.of("studio", "marble", "pastel", "nature", "luxury")) {
			assertThat(Workflow.PRODUCT_EXACT.findStyle(id)).as(id).isPresent();
		}
		for (String id : List.of("studio", "lifestyle", "nature", "luxury")) {
			assertThat(Workflow.PRODUCT_PHOTO.findStyle(id)).as(id).isPresent();
		}
		for (String id : List.of("showcase", "splash", "fabric")) {
			assertThat(Workflow.PRODUCT_VIDEO.findStyle(id)).as(id).isPresent();
		}
		assertThat(Workflow.PRODUCT_PHOTO.findStyle("lifestyle").orElseThrow().id()).isEqualTo("marble");
	}

	@Test
	void creativeScenesAskToKeepTheLabel() {
		assertThat(Workflow.PRODUCT_PHOTO.getStyles())
				.allSatisfy(s -> assertThat(s.prompt()).startsWith("Place this exact product").contains("keep product label"));
		assertThat(Workflow.PRODUCT_EXACT.getStyles()).noneMatch(s -> s.prompt().contains("Place this exact product"));
	}
}
