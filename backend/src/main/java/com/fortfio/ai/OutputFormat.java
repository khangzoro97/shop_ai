package com.fortfio.ai;

import lombok.Getter;

@Getter
public enum OutputFormat {

	SQUARE("Vuông 1:1 · Shopee, Lazada", 1200, 1200, "square_hd"),
	PORTRAIT("Dọc 3:4 · Facebook, Instagram", 1080, 1440, "portrait_4_3"),
	STORY("Dọc 9:16 · TikTok, Reels", 1080, 1920, "portrait_16_9");

	private final String label;
	private final int width;
	private final int height;
	/** fal image_size preset with the same aspect ratio. */
	private final String falImageSize;

	OutputFormat(String label, int width, int height, String falImageSize) {
		this.label = label;
		this.width = width;
		this.height = height;
		this.falImageSize = falImageSize;
	}
}
