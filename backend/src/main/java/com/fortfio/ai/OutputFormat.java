package com.fortfio.ai;

import java.util.Map;

import lombok.Getter;

@Getter
public enum OutputFormat {

	SQUARE("Vuông 1:1 · Shopee, Lazada", 1200, 1200),
	PORTRAIT("Dọc 3:4 · Facebook, Instagram", 1080, 1440),
	STORY("Dọc 9:16 · TikTok, Reels", 1080, 1920);

	private static final int SIZE_STEP = 16;
	private static final double MAX_BACKDROP_PIXELS = 1_000_000;

	private final String label;
	private final int width;
	private final int height;

	OutputFormat(String label, int width, int height) {
		this.label = label;
		this.width = width;
		this.height = height;
	}

	/**
	 * Backdrop in the final aspect ratio but capped below 1 megapixel: fal bills each started
	 * megapixel, and the backdrop is only scenery behind the full-resolution product.
	 */
	public Map<String, Integer> falImageSize() {
		double scale = Math.min(1, Math.sqrt(MAX_BACKDROP_PIXELS / ((double) width * height)));
		return Map.of("width", roundDown(width * scale), "height", roundDown(height * scale));
	}

	private static int roundDown(double value) {
		return (int) value / SIZE_STEP * SIZE_STEP;
	}
}
