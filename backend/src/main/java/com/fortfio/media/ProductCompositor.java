package com.fortfio.media;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Component;

import com.fortfio.ai.OutputFormat;

import lombok.RequiredArgsConstructor;

/**
 * Places the real product cutout onto an AI backdrop. The product is only scaled, never redrawn,
 * so labels, text and packaging colors stay exactly as photographed.
 */
@Component
@RequiredArgsConstructor
public class ProductCompositor {

	private static final double MAX_PRODUCT_WIDTH = 0.62;
	private static final double MAX_PRODUCT_HEIGHT = 0.60;
	private static final double MAX_UPSCALE = 2.0;
	private static final double BASELINE = 0.84;
	private static final int ALPHA_THRESHOLD = 8;
	private static final float JPEG_QUALITY = 0.92f;

	private final ImageLoader imageLoader;
	private final FileStorage storage;

	public String composeAndStore(String cutoutUrl, String backdropUrl, OutputFormat format) {
		BufferedImage result = compose(imageLoader.load(backdropUrl), imageLoader.load(cutoutUrl),
				format.getWidth(), format.getHeight());
		return storage.save(toJpeg(result), "jpg");
	}

	public static BufferedImage compose(BufferedImage backdrop, BufferedImage product, int width, int height) {
		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		try {
			applyQualityHints(g);
			drawCover(g, backdrop, width, height);

			BufferedImage trimmed = trimTransparent(toArgb(product));
			double scale = Math.min(MAX_UPSCALE, Math.min(
					width * MAX_PRODUCT_WIDTH / trimmed.getWidth(),
					height * MAX_PRODUCT_HEIGHT / trimmed.getHeight()));
			BufferedImage scaled = resize(trimmed, (int) Math.round(trimmed.getWidth() * scale),
					(int) Math.round(trimmed.getHeight() * scale));

			int x = (width - scaled.getWidth()) / 2;
			int bottom = (int) Math.round(height * BASELINE);
			int y = bottom - scaled.getHeight();

			drawContactShadow(g, x + scaled.getWidth() / 2.0, bottom, scaled.getWidth());
			drawSoftShadow(g, scaled, x, y);
			g.drawImage(scaled, x, y, null);
		} finally {
			g.dispose();
		}
		return canvas;
	}

	public static byte[] toJpeg(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static void drawCover(Graphics2D g, BufferedImage image, int width, int height) {
		double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
		int w = (int) Math.ceil(image.getWidth() * scale);
		int h = (int) Math.ceil(image.getHeight() * scale);
		g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
	}

	private static void drawContactShadow(Graphics2D g, double centerX, double baseY, int productWidth) {
		double radiusX = productWidth * 0.48;
		double radiusY = Math.max(6, productWidth * 0.045);
		AffineTransform saved = g.getTransform();
		g.translate(centerX, baseY);
		g.scale(radiusX, radiusY);
		g.setPaint(new RadialGradientPaint(0f, 0f, 1f, new float[] { 0f, 1f },
				new Color[] { new Color(0, 0, 0, 115), new Color(0, 0, 0, 0) }));
		g.fill(new Ellipse2D.Double(-1, -1, 2, 2));
		g.setTransform(saved);
	}

	/** Blurred silhouette, computed at quarter resolution to keep the blur cheap on large images. */
	private static void drawSoftShadow(Graphics2D g, BufferedImage product, int x, int y) {
		int factor = 4;
		int radius = Math.max(2, product.getHeight() / factor / 30);
		int margin = radius * 3;
		int w = Math.max(1, product.getWidth() / factor);
		int h = Math.max(1, product.getHeight() / factor);

		BufferedImage silhouette = new BufferedImage(w + margin * 2, h + margin * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = silhouette.createGraphics();
		applyQualityHints(sg);
		sg.drawImage(product, margin, margin, w, h, null);
		sg.setComposite(AlphaComposite.SrcIn);
		sg.setColor(new Color(0, 0, 0, 70));
		sg.fillRect(0, 0, silhouette.getWidth(), silhouette.getHeight());
		sg.dispose();

		BufferedImage blurred = boxBlur(boxBlur(silhouette, radius), radius);
		int offsetX = (int) Math.round(product.getWidth() * 0.025);
		int offsetY = (int) Math.round(product.getHeight() * 0.015);
		g.drawImage(blurred, x - margin * factor + offsetX, y - margin * factor + offsetY,
				blurred.getWidth() * factor, blurred.getHeight() * factor, null);
	}

	private static BufferedImage boxBlur(BufferedImage image, int radius) {
		int size = radius * 2 + 1;
		float[] weights = new float[size];
		Arrays.fill(weights, 1f / size);
		BufferedImage horizontal = new ConvolveOp(new Kernel(size, 1, weights), ConvolveOp.EDGE_NO_OP, null)
				.filter(image, null);
		return new ConvolveOp(new Kernel(1, size, weights), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null);
	}

	private static BufferedImage trimTransparent(BufferedImage image) {
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) > ALPHA_THRESHOLD) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			throw new IllegalStateException("Không tách được sản phẩm khỏi nền");
		}
		return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/** Halves repeatedly before the final step: a single large bicubic downscale aliases fine label text. */
	private static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
			current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return current.getWidth() == width && current.getHeight() == height ? current : draw(current, width, height);
	}

	private static BufferedImage draw(BufferedImage image, int width, int height) {
		BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		applyQualityHints(g);
		g.drawImage(image, 0, 0, out.getWidth(), out.getHeight(), null);
		g.dispose();
		return out;
	}

	private static BufferedImage toArgb(BufferedImage image) {
		return image.getType() == BufferedImage.TYPE_INT_ARGB ? image : draw(image, image.getWidth(), image.getHeight());
	}

	private static void applyQualityHints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	}
}
