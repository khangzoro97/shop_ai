package com.fortfio.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import javax.imageio.ImageIO;

public final class TestImages {

	public static final Color PRODUCT_RED = new Color(220, 20, 60);
	public static final Color BACKDROP_BLUE = new Color(30, 60, 200);

	private TestImages() {
	}

	/** 200x300 transparent canvas with an opaque 100x200 product rectangle in it. */
	public static BufferedImage redProduct() {
		BufferedImage image = new BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(PRODUCT_RED);
		g.fillRect(50, 50, 100, 200);
		g.dispose();
		return image;
	}

	public static BufferedImage blueBackdrop() {
		BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(BACKDROP_BLUE);
		g.fillRect(0, 0, 600, 400);
		g.dispose();
		return image;
	}

	public static String redProductDataUrl() {
		return dataUrl(redProduct());
	}

	public static String blueBackdropDataUrl() {
		return dataUrl(blueBackdrop());
	}

	private static String dataUrl(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
