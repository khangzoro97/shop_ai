package com.fortfio.ai;

import java.util.Map;

import lombok.Getter;

@Getter
public enum OutputFormat {

	SQUARE("Vuông 1:1 · Shopee, Lazada", 1200, 1200),
	PORTRAIT("Dọc 3:4 · Facebook, Instagram", 1080, 1440),
	STORY("Dọc 9:16 · TikTok, Reels", 1080, 1920);

	private static final int SIZE_STEP = 16;

	private final String label;
	private final int width;
	private final int height;

	OutputFormat(String label, int width, int height) {
		this.label = label;
		this.width = width;
		this.height = height;
	}

	/**
	 * Backdrop generated at (at least) the final size, rounded up to the model's size step, so it
	 * is never upscaled; fal's presets are only ~1 megapixel, which blurs 9:16 outputs.
	 */
	public Map<String, Integer> falImageSize() {
		return Map.of("width", roundUp(width), "height", roundUp(height));
	}

	private static int roundUp(int value) {
		return (value + SIZE_STEP - 1) / SIZE_STEP * SIZE_STEP;
	}
}
