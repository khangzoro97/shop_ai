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
			OutputType.IMAGE,
			Pipeline.COMPOSITE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm")),
			List.of(
					new Style("studio", "Studio sáng",
							"Empty seamless white and light grey studio backdrop with a smooth tabletop surface, soft diffused lighting, subtle gradient."),
					new Style("marble", "Bàn đá cẩm thạch",
							"Empty white marble countertop in a bright bathroom, warm morning sunlight from the left, soft shadows, blurred background."),
					new Style("pastel", "Hồng pastel",
							"Soft pastel pink backdrop with a round matte podium in the center, minimal aesthetic, soft even light."),
					new Style("nature", "Thiên nhiên tươi mát",
							"Flat beige stone podium surrounded by fresh green leaves and white flowers at the edges, water droplets, bright natural light."),
					new Style("luxury", "Sang trọng",
							"Dark elegant backdrop with flowing champagne silk fabric and gold accents at the edges, empty pedestal in the center, soft rim light."))),

	PRODUCT_PHOTO(
			"Ảnh sản phẩm sáng tạo",
			"AI vẽ lại toàn bộ khung hình, bối cảnh tự nhiên hơn nhưng nhãn và chữ có thể bị lệch. Cần kiểm tra kỹ trước khi giao.",
			2,
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm")),
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
			OutputType.IMAGE,
			Pipeline.SINGLE,
			List.of(
					new ImageSlot("person", "Ảnh người mẫu"),
					new ImageSlot("garment", "Ảnh trang phục")),
			List.of()),

	PRODUCT_VIDEO(
			"Video sản phẩm ngắn",
			"Tạo clip 5 giây từ ảnh sản phẩm để đăng TikTok, Reels.",
			20,
			OutputType.VIDEO,
			Pipeline.SINGLE,
			List.of(new ImageSlot("product", "Ảnh sản phẩm")),
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
	private final OutputType outputType;
	private final Pipeline pipeline;
	private final List<ImageSlot> imageSlots;
	private final List<Style> styles;

	Workflow(String label, String description, int credits, OutputType outputType, Pipeline pipeline,
			List<ImageSlot> imageSlots, List<Style> styles) {
		this.label = label;
		this.description = description;
		this.credits = credits;
		this.outputType = outputType;
		this.pipeline = pipeline;
		this.imageSlots = imageSlots;
		this.styles = styles;
	}

	public Optional<Style> findStyle(String id) {
		return styles.stream().filter(s -> s.id().equals(id)).findFirst();
	}

	public List<OutputFormat> formats() {
		return pipeline == Pipeline.COMPOSITE ? List.of(OutputFormat.values()) : List.of();
	}

	public enum OutputType {
		IMAGE, VIDEO
	}

	public enum Pipeline {
		/** One provider call produces the result. */
		SINGLE,
		/** Cutout and backdrop are generated in parallel, then composited locally so product pixels stay untouched. */
		COMPOSITE
	}

	public record ImageSlot(String key, String label) {
	}

	public record Style(String id, String label, String prompt) {
	}
}
