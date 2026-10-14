package com.fortfio.trial;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import com.fortfio.media.ProductCompositor;

import lombok.extern.slf4j.Slf4j;

/** Downscaled preview with a diagonal "Chụp Xịn" pattern, so trial results are not usable as-is. */
@Slf4j
final class Watermark {

	static final String TEXT = "CHỤP XỊN · ẢNH THỬ";

	private Watermark() {
	}

	static byte[] preview(byte[] original, int maxSide) {
		BufferedImage source;
		try {
			source = ImageIO.read(new ByteArrayInputStream(original));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (source == null) {
			throw new IllegalArgumentException("Not an image");
		}
		double scale = Math.min(1, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.drawImage(source, 0, 0, width, height, null);
			try {
				drawText(g, width, height);
			} catch (RuntimeException | Error ex) {
				// Servers without fonts cannot draw text; the stripes still mark the preview.
				log.warn("Watermark text unavailable, using stripes only: {}", ex.toString());
				drawStripes(g, width, height);
			}
		} finally {
			g.dispose();
		}
		return ProductCompositor.toJpeg(canvas);
	}

	private static void drawText(Graphics2D g, int width, int height) {
		int fontSize = Math.max(14, Math.min(width, height) / 14);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
		FontMetrics metrics = g.getFontMetrics();
		int textWidth = metrics.stringWidth(TEXT);
		int stepX = textWidth + fontSize * 2;
		int stepY = fontSize * 4;
		int diagonal = (int) Math.hypot(width, height);

		AffineTransform saved = g.getTransform();
		g.rotate(Math.toRadians(-30), width / 2.0, height / 2.0);
		for (int y = -diagonal / 2; y < height + diagonal / 2; y += stepY) {
			int offset = (y / stepY) % 2 == 0 ? 0 : stepX / 2;
			for (int x = -diagonal / 2 - offset; x < width + diagonal / 2; x += stepX) {
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
				g.setColor(Color.BLACK);
				g.drawString(TEXT, x + 2, y + 2);
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
				g.setColor(Color.WHITE);
				g.drawString(TEXT, x, y);
			}
		}
		g.setTransform(saved);
		g.setComposite(AlphaComposite.SrcOver);
	}

	private static void drawStripes(Graphics2D g, int width, int height) {
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
		g.setColor(Color.WHITE);
		int gap = Math.max(24, Math.min(width, height) / 8);
		g.setStroke(new BasicStroke(Math.max(4, gap / 6f)));
		for (int x = -height; x < width; x += gap) {
			g.drawLine(x, height, x + height, 0);
		}
		g.setComposite(AlphaComposite.SrcOver);
	}
}
