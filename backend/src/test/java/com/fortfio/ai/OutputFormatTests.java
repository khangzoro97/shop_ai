package com.fortfio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OutputFormatTests {

	@Test
	void backdropStaysUnderOneMegapixelInTheFinalAspectRatio() {
		assertThat(OutputFormat.SQUARE.falImageSize()).isEqualTo(Map.of("width", 992, "height", 992));
		assertThat(OutputFormat.PORTRAIT.falImageSize()).isEqualTo(Map.of("width", 864, "height", 1152));
		assertThat(OutputFormat.STORY.falImageSize()).isEqualTo(Map.of("width", 736, "height", 1328));

		for (OutputFormat format : OutputFormat.values()) {
			Map<String, Integer> size = format.falImageSize();
			assertThat(size.get("width") * size.get("height")).isLessThan(1_000_000);
			assertThat((double) size.get("width") / size.get("height"))
					.isCloseTo((double) format.getWidth() / format.getHeight(), offset(0.02));
		}
	}
}
