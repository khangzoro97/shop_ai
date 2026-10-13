package com.fortfio.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
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
	void dropsSmallDetachedFragmentsButKeepsProductSets() {
		BufferedImage cutout = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = cutout.createGraphics();
		g.setColor(TestImages.PRODUCT_RED);
		g.fillRect(10, 10, 80, 180);
		g.fillRect(110, 20, 80, 170);
		g.fillRect(250, 10, 20, 20);
		g.dispose();

		BufferedImage cleaned = ProductCompositor.dropSmallFragments(cutout);

		assertThat(cleaned.getRGB(50, 100) >>> 24).isEqualTo(255);
		assertThat(cleaned.getRGB(150, 100) >>> 24).isEqualTo(255);
		assertThat(cleaned.getRGB(260, 20) >>> 24).isZero();
	}

	@Test
	void rejectsFullyTransparentCutout() {
		BufferedImage empty = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

		assertThatThrownBy(() -> ProductCompositor.compose(TestImages.blueBackdrop(), empty, 1200, 1200))
				.isInstanceOf(IllegalStateException.class);
	}
}
