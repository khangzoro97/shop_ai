package com.fortfio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LabelTextCheckTests {

	@Test
	void flagsChangedNumbersAndMisspelledWords() {
		assertThat(LabelTextCheck.missingTokens(
				"Nécessaire The Body Lotion FRAGRANCE-FREE 200 ml / 6.8 fl oz",
				"Necessaire The Body Lotion FRAGANCE FREE 340 ml / 6,8 fl oz"))
				.containsExactly("FRAGRANCE", "200");
	}

	@Test
	void ignoresShortWordsCaseAndAccents() {
		assertThat(LabelTextCheck.missingTokens("Serum SPF 50 cho da", "SERUM spf 50")).isEmpty();
	}

	@Test
	void emptySourceHasNothingToCompare() {
		assertThat(LabelTextCheck.missingTokens(null, "anything")).isEmpty();
		assertThat(LabelTextCheck.missingTokens("", "")).isEmpty();
	}
}
