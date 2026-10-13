package com.fortfio.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ProductCompositorTests {

	@Test
	void placesTrimmedProductCenteredOnBaseline() {
		BufferedImage result = ProductCompositor.compose(TestImages.blueBackdrop(), TestImages.redProduct(), 1200, 1200);

		assertThat(result.getWidth()).isEqualTo(1200);
		assertThat(result.getHeight()).isEqualTo(1200);
		// Trimmed product is 101x201, upscaled 2x (the cap) to 202x402, bottom at 84% of the height.
		assertThat(new Color(result.getRGB(600, 807))).isEqualTo(TestImages.PRODUCT_RED);
		assertThat(new Color(result.getRGB(600, 1008 - 3))).isEqualTo(TestImages.PRODUCT_RED);
		assertThat(new Color(result.getRGB(10, 10))).isEqualTo(TestImages.BACKDROP_BLUE);
	}

	@Test
	void shadowDarkensBackdropUnderProduct() {
		BufferedImage result = ProductCompositor.compose(TestImages.blueBackdrop(), TestImages.redProduct(), 1200, 1200);

		Color belowProduct = new Color(result.getRGB(600, 1012));
		assertThat(belowProduct.getBlue()).isLessThan(TestImages.BACKDROP_BLUE.getBlue());
	}

	@Test
	void rejectsFullyTransparentCutout() {
		BufferedImage empty = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

		assertThatThrownBy(() -> ProductCompositor.compose(TestImages.blueBackdrop(), empty, 1200, 1200))
				.isInstanceOf(IllegalStateException.class);
	}
}
