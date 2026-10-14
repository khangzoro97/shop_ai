package com.fortfio.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;

@Getter
public enum Workflow {

	PRODUCT_EXACT(
			"Ảnh sản phẩm giữ nguyên nhãn",
			"Giữ đúng 100% sản phẩm thật (nhãn, chữ, màu bao bì), AI chỉ tạo bối cảnh. Hợp cho mỹ phẩm, chai lọ, hộp.",
			2,
			4,
			OutputType.IMAGE,
			Pipeline.PRESERVE_PRODUCT,
			List.of(
					new ImageSlot("product", "Ảnh sản phẩm", true),
					new ImageSlot("scene", "Ảnh bối cảnh mẫu", false)),
			Vibes.SCENES),

	PRODUCT_PHOTO(
			"Ảnh sản phẩm sáng tạo",
			"AI vẽ lại toàn bộ khung hình, bối cảnh tự nhiên hơn nhưng nhãn và chữ có thể bị lệch. Hệ thống tự cảnh báo khi chữ bị đổi.",
			2,
			4,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm", true)),
			Vibes.CREATIVE_SCENES),

	PRODUCT_HOLDING(
			"Người mẫu cầm sản phẩm",
			"Đặt sản phẩm vào tay người mẫu trong ảnh của bạn, giữ nguyên gương mặt, trang phục và bối cảnh. Hợp cho TikTok, ảnh review. Chữ nhỏ trên nhãn có thể bị lệch, hệ thống tự cảnh báo.",
			2,
			1,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(
					new ImageSlot("person", "Ảnh người mẫu", true),
					new ImageSlot("product", "Ảnh sản phẩm", true)),
			Vibes.HOLDING_POSES),

	VIRTUAL_TRYON(
			"Thử đồ ảo",
			"Mặc sản phẩm thời trang của shop lên người mẫu, không cần buổi chụp. Giữ đúng hình in và chữ trên áo.",
			3,
			4,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(
					new ImageSlot("person", "Ảnh người mẫu", true),
					new ImageSlot("garment", "Ảnh trang phục", true)),
			Vibes.GARMENT_TYPES),

	PRODUCT_VIDEO(
			"Video sản phẩm ngắn",
			"Video 4–30 giây từ ảnh sản phẩm, dọc 9:16 cho TikTok, Reels, có nhạc nền. Ảnh khác tỉ lệ được AI mở rộng khung, sản phẩm giữ nguyên.",
			20,
			1,
			OutputType.VIDEO,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm", true)),
			Vibes.MOTIONS),

	RELIGHT(
			"Chỉnh ánh sáng",
			"Đổi hướng và kiểu ánh sáng của ảnh đã tạo. AI vẽ lại ảnh nên chữ nhỏ có thể bị lệch, hệ thống tự cảnh báo.",
			2,
			1,
			OutputType.IMAGE,
			Pipeline.ENHANCE,
			List.of(new ImageSlot("image", "Ảnh cần chỉnh", true)),
			Vibes.LIGHTS),

	UPSCALE_2K(
			"Phóng to 2K",
			"Phóng ảnh lên cạnh dài 2048px, nét hơn khi in hoặc đăng banner. Không khôi phục được chữ quá nhỏ.",
			3,
			1,
			OutputType.IMAGE,
			Pipeline.ENHANCE,
			List.of(new ImageSlot("image", "Ảnh cần phóng to", true)),
			List.of()),

	UPSCALE_4K(
			"Phóng to 4K",
			"Phóng ảnh lên cạnh dài 4096px cho in ấn khổ lớn. Không khôi phục được chữ quá nhỏ.",
			12,
			1,
			OutputType.IMAGE,
			Pipeline.ENHANCE,
			List.of(new ImageSlot("image", "Ảnh cần phóng to", true)),
			List.of());

	/** Style ids that were renamed; old jobs keep the old id and must stay retryable. */
	private static final Map<String, String> RENAMED_STYLES = Map.of("lifestyle", "marble");

	private final String label;
	private final String description;
	private final int credits;
	private final int maxVariants;
	private final OutputType outputType;
	private final Pipeline pipeline;
	private final List<ImageSlot> imageSlots;
	private final List<Style> styles;

	Workflow(String label, String description, int credits, int maxVariants, OutputType outputType, Pipeline pipeline,
			List<ImageSlot> imageSlots, List<Style> styles) {
		this.label = label;
		this.description = description;
		this.credits = credits;
		this.maxVariants = maxVariants;
		this.outputType = outputType;
		this.pipeline = pipeline;
		this.imageSlots = imageSlots;
		this.styles = styles;
	}

	public Optional<Style> findStyle(String id) {
		String current = RENAMED_STYLES.getOrDefault(id, id);
		return styles.stream().filter(s -> s.id().equals(current)).findFirst();
	}

	public boolean preservesProduct() {
		return pipeline == Pipeline.PRESERVE_PRODUCT;
	}

	public boolean enhancesResult() {
		return pipeline == Pipeline.ENHANCE;
	}

	public boolean isVideo() {
		return outputType == OutputType.VIDEO;
	}

	/** The first format is the default. */
	public List<OutputFormat> formats() {
		if (isVideo()) {
			return List.of(OutputFormat.STORY, OutputFormat.PORTRAIT, OutputFormat.SQUARE);
		}
		return preservesProduct() ? List.of(OutputFormat.values()) : List.of();
	}

	public List<Placement> placements() {
		return preservesProduct() ? List.of(Placement.values()) : List.of();
	}

	public enum OutputType {
		IMAGE, VIDEO
	}

	public enum Pipeline {
		/** One provider call produces the result. */
		SINGLE,
		/** The uploaded product pixels are kept as-is; AI only creates the scene around them. */
		PRESERVE_PRODUCT,
		/** Edits a finished result: started from a result card, not from the Studio form. */
		ENHANCE
	}

	public record ImageSlot(String key, String label, boolean required) {
	}

	/**
	 * @param group shown as a tab in the Studio
	 * @param hint  which products the vibe suits, shown to shop owners
	 */
	public record Style(String id, String group, String label, String hint, String prompt) {
	}
}
