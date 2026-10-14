package com.fortfio.ai;

import lombok.Getter;

/** Background music generated for a video; instrumental so it never clashes with a voice-over. */
@Getter
public enum MusicMood {

	UPBEAT("Sôi động", "Bắt trend TikTok, flash sale",
			"Upbeat modern pop instrumental for a product ad, bright plucks, punchy light percussion, catchy and energetic"),
	CHILL("Nhẹ nhàng", "Skincare, đồ dùng hằng ngày",
			"Chill lo-fi instrumental, soft warm keys, relaxed gentle beat, calm and fresh"),
	LUXURY("Sang trọng", "Nước hoa, mỹ phẩm cao cấp",
			"Elegant luxury instrumental, smooth deep piano and soft strings, minimal sophisticated beat, premium fashion ad"),
	CUTE("Dễ thương", "Đồ cho bé, phụ kiện xinh",
			"Cute playful instrumental, ukulele, glockenspiel and soft claps, cheerful and sweet"),
	FESTIVE("Lễ hội, Tết", "Tết, Noel, mùa sale lớn",
			"Festive celebratory instrumental, joyful bells, bright brass and upbeat percussion, holiday sale ad"),
	CINEMATIC("Điện ảnh", "Ra mắt sản phẩm mới",
			"Cinematic product reveal instrumental, rising strings, airy synth pads, building to an inspiring finish");

	private static final String SUFFIX = ", instrumental only, no vocals, clean mix for social media video";

	private final String label;
	private final String hint;
	private final String prompt;

	MusicMood(String label, String hint, String prompt) {
		this.label = label;
		this.hint = hint;
		this.prompt = prompt + SUFFIX;
	}
}
