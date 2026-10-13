package com.fortfio.ai;

import java.util.List;
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
			List.of(
					new Style("studio", "Studio sáng",
							"Seamless white and light grey studio backdrop with a smooth tabletop surface, soft diffused lighting, subtle gradient."),
					new Style("marble", "Bàn đá cẩm thạch",
							"White marble countertop in a bright bathroom, warm morning sunlight from the left, soft shadows, blurred background."),
					new Style("pastel", "Hồng pastel",
							"Soft pastel pink backdrop with a round matte podium, minimal aesthetic, soft even light."),
					new Style("nature", "Thiên nhiên tươi mát",
							"Flat beige stone podium surrounded by fresh green leaves and white flowers, water droplets, bright natural light."),
					new Style("luxury", "Sang trọng",
							"Dark elegant backdrop with flowing champagne silk fabric and gold accents, dark marble pedestal, soft rim light."))),

	PRODUCT_PHOTO(
			"Ảnh sản phẩm sáng tạo",
			"AI vẽ lại toàn bộ khung hình, bối cảnh tự nhiên hơn nhưng nhãn và chữ có thể bị lệch. Hệ thống tự cảnh báo khi chữ bị đổi.",
			2,
			4,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm", true)),
			List.of(
					new Style("studio", "Studio nền trắng",
							"Place this exact product on a clean seamless white studio background, softbox lighting, subtle reflection, e-commerce packshot, ultra sharp, keep product label and shape unchanged."),
					new Style("lifestyle", "Lifestyle bàn đá",
							"Place this exact product on a white marble bathroom counter, warm morning sunlight through a window, soft natural shadows, lifestyle cosmetics photography, keep product label and shape unchanged."),
					new Style("nature", "Thiên nhiên tươi mát",
							"Place this exact product among fresh green leaves, flower petals and water droplets, bright natural light, clean beauty aesthetic, keep product label and shape unchanged."),
					new Style("luxury", "Sang trọng",
							"Place this exact product on a dark elegant background with gold accents and silk fabric, dramatic rim light, luxury beauty campaign, keep product label and shape unchanged."))),

	VIRTUAL_TRYON(
			"Thử đồ ảo",
			"Mặc sản phẩm thời trang của shop lên người mẫu, không cần buổi chụp.",
			3,
			1,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(
					new ImageSlot("person", "Ảnh người mẫu", true),
					new ImageSlot("garment", "Ảnh trang phục", true)),
			List.of()),

	PRODUCT_VIDEO(
			"Video sản phẩm ngắn",
			"Tạo clip 5 giây từ ảnh sản phẩm để đăng TikTok, Reels.",
			20,
			1,
			OutputType.VIDEO,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm", true)),
			List.of(
					new Style("showcase", "Xoay giới thiệu",
							"Slow cinematic camera orbit around the product, soft studio lighting, gentle light sweep across the packaging, premium commercial."),
					new Style("splash", "Hiệu ứng nước",
							"Product stands still while fresh water splashes around it in slow motion, bright clean beauty commercial."),
					new Style("fabric", "Vải bay chuyển động",
							"Flowing silk fabric moves gracefully in the breeze, soft light, elegant fashion commercial, smooth camera push-in.")));

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
		return styles.stream().filter(s -> s.id().equals(id)).findFirst();
	}

	public boolean preservesProduct() {
		return pipeline == Pipeline.PRESERVE_PRODUCT;
	}

	public List<OutputFormat> formats() {
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
		PRESERVE_PRODUCT
	}

	public record ImageSlot(String key, String label, boolean required) {
	}

	public record Style(String id, String label, String prompt) {
	}
}
