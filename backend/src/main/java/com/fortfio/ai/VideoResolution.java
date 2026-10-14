package com.fortfio.ai;

import lombok.Getter;

/** Credits per second follow the per-second price of the video model at each resolution. */
@Getter
public enum VideoResolution {

	P360("360p", "Xem thử ý tưởng, chữ nhỏ trên nhãn dễ nhòe", 2),
	P720("720p", "Chuẩn TikTok, Reels, Shopee Video", 4),
	P1080("1080p", "Nét nhất, cho quảng cáo", 6);

	private final String label;
	private final String hint;
	private final int creditsPerSecond;

	VideoResolution(String label, String hint, int creditsPerSecond) {
		this.label = label;
		this.hint = hint;
		this.creditsPerSecond = creditsPerSecond;
	}
}
