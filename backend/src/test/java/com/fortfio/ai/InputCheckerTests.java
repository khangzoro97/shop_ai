package com.fortfio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputCheckerTests {

	@Test
	void readsTheModelAnswerEvenWhenWrappedInMarkdown() {
		InputChecker.Findings found = InputChecker.parse("""
				```json
				{"banner": true, "multipleProducts": false, "wornByPerson": false, "person": false, "blurry": true}
				```""");

		assertThat(InputChecker.issues(Workflow.PRODUCT_EXACT, "product", found))
				.extracting(InputChecker.Issue::code)
				.containsExactly("BANNER", "BLURRY");
	}

	@Test
	void unreadableAnswersGiveNoWarnings() {
		assertThat(InputChecker.issues(Workflow.PRODUCT_EXACT, "product", InputChecker.parse("Không rõ")))
				.isEmpty();
		assertThat(InputChecker.issues(Workflow.PRODUCT_EXACT, "product", InputChecker.parse(null))).isEmpty();
	}

	@Test
	void suggestsTheModelTabsForPeopleInProductPhotos() {
		InputChecker.Findings worn = new InputChecker.Findings(false, false, true, true, false);
		InputChecker.Findings holding = new InputChecker.Findings(false, false, false, true, false);

		assertThat(InputChecker.issues(Workflow.PRODUCT_EXACT, "product", worn))
				.singleElement()
				.extracting(InputChecker.Issue::suggest)
				.isEqualTo(Workflow.VIRTUAL_TRYON);
		assertThat(InputChecker.issues(Workflow.PRODUCT_EXACT, "product", holding))
				.singleElement()
				.extracting(InputChecker.Issue::suggest)
				.isEqualTo(Workflow.PRODUCT_HOLDING);
		assertThat(InputChecker.issues(Workflow.PRODUCT_HOLDING, "product", holding)).isEmpty();
	}

	@Test
	void onlyProductAndGarmentPhotosAreChecked() {
		assertThat(InputChecker.checks(Workflow.PRODUCT_EXACT, "product")).isTrue();
		assertThat(InputChecker.checks(Workflow.VIRTUAL_TRYON, "garment")).isTrue();
		assertThat(InputChecker.checks(Workflow.PRODUCT_HOLDING, "person")).isFalse();
		assertThat(InputChecker.checks(Workflow.PRODUCT_HOLDING, "product")).isTrue();
		assertThat(InputChecker.checks(Workflow.VIRTUAL_TRYON, "person")).isFalse();
	}
}
