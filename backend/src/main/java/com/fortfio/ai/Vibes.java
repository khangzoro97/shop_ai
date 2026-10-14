package com.fortfio.ai;

import java.util.Arrays;
import java.util.List;

import com.fortfio.ai.Workflow.Style;

/**
 * Style presets offered in the Studio, one list per workflow. Scene descriptions are written for Bria product shot,
 * which places the real product into them; creative photos wrap the same scenes in an edit instruction.
 * Ids are stored on jobs and used by retry, so they must never be renamed.
 */
final class Vibes {

	private static final String ECOMMERCE = "Studio sàn TMĐT";
	private static final String LIFESTYLE = "Đời sống";
	private static final String NATURE = "Thiên nhiên";
	private static final String PREMIUM = "Cao cấp";
	private static final String SEASONAL = "Mùa lễ và sale";
	private static final String CAMERA = "Chuyển động máy quay";
	private static final String EFFECTS = "Hiệu ứng";
	private static final String HOLDING = "Cách cầm";
	private static final String GARMENT = "Loại đồ";
	private static final String LIGHT = "Ánh sáng";

	private static final String CREATIVE_PREFIX = "Place this exact product in the following scene: ";
	private static final String CREATIVE_SUFFIX = " Professional product photography, realistic lighting and shadows,"
			+ " keep product label and shape unchanged.";

	static final List<Style> SCENES = List.of(
			new Style("studio", ECOMMERCE, "Studio sáng", "Ảnh sản phẩm chuẩn, dùng được mọi nơi",
					"Seamless white and light grey studio backdrop with a smooth tabletop surface, soft diffused lighting, subtle gradient."),
			new Style("white", ECOMMERCE, "Nền trắng chuẩn sàn", "Ảnh bìa Shopee, Lazada, TikTok Shop",
					"Pure white seamless background, clean e-commerce packshot, soft even lighting from both sides, gentle natural contact shadow under the product."),
			new Style("colorpop", ECOMMERCE, "Nền màu nổi bật", "Ảnh quảng cáo bắt mắt trên newsfeed",
					"Bold saturated solid coral backdrop and matching tabletop, hard direct light with a crisp graphic shadow, modern pop advertising look."),
			new Style("podium", ECOMMERCE, "Bục hình khối", "Trưng bày gọn gàng, hiện đại",
					"Stacked geometric podiums, a cylinder and a cube in soft beige and sage green, minimal set design, soft studio light, clean modern product display."),
			new Style("pastel", ECOMMERCE, "Hồng pastel", "Mỹ phẩm dễ thương, khách hàng trẻ",
					"Soft pastel pink backdrop with a round matte podium, minimal aesthetic, soft even light."),

			new Style("marble", LIFESTYLE, "Bàn đá cẩm thạch", "Skincare, sữa tắm, đồ phòng tắm",
					"White marble countertop in a bright bathroom, warm morning sunlight from the left, soft shadows, blurred background."),
			new Style("vanity", LIFESTYLE, "Bàn trang điểm", "Son, phấn, nước hoa",
					"Elegant vanity table with a round mirror, softly blurred makeup brushes and a small vase of flowers, warm indoor light, feminine cozy bedroom atmosphere."),
			new Style("wood", LIFESTYLE, "Bàn gỗ ấm áp", "Đồ handmade, nến thơm, sản phẩm mộc",
					"Warm natural oak wooden table near a window, linen cloth and a softly blurred ceramic cup, golden afternoon light, cozy homely mood."),
			new Style("bedroom", LIFESTYLE, "Ga giường buổi sáng", "Kem dưỡng đêm, đồ ngủ, chăm sóc bản thân",
					"Soft white linen bedsheets with gentle folds, early morning sunlight casting window shadows, calm and fresh self-care morning."),
			new Style("spa", LIFESTYLE, "Spa thư giãn", "Tinh dầu, mặt nạ, sản phẩm thư giãn",
					"Calm spa setting with rolled white towels, smooth grey stones, a lit candle and eucalyptus leaves, soft warm light, relaxing wellness mood."),

			new Style("nature", NATURE, "Lá xanh tươi mát", "Mỹ phẩm thiên nhiên, organic",
					"Flat beige stone podium surrounded by fresh green leaves and white flowers, water droplets, bright natural light."),
			new Style("beach", NATURE, "Biển mùa hè", "Kem chống nắng, đồ bơi, sản phẩm mùa hè",
					"Light sand beach with gentle turquoise waves in the background, bright summer sunlight, palm leaf shadows, fresh vacation vibe."),
			new Style("water", NATURE, "Mặt nước gợn sóng", "Serum cấp ẩm, toner, xịt khoáng",
					"Product standing in shallow crystal clear water with soft ripples and reflections, light blue tones, fresh hydrating skincare mood."),
			new Style("zen", NATURE, "Đá và rêu", "Mỹ phẩm thuần chay, thảo mộc",
					"Smooth river stones and soft green moss, misty forest light, organic calm natural skincare mood."),
			new Style("fruit", NATURE, "Trái cây tươi", "Vitamin C, sản phẩm hương trái cây",
					"Fresh sliced oranges and lemons around the product, light peach background, bright cheerful daylight, juicy vitamin freshness."),

			new Style("luxury", PREMIUM, "Lụa champagne", "Nước hoa, mỹ phẩm cao cấp",
					"Dark elegant backdrop with flowing champagne silk fabric and gold accents, dark marble pedestal, soft rim light."),
			new Style("blackstone", PREMIUM, "Đá đen tối giản", "Đồ nam, nước hoa, phụ kiện",
					"Matte black stone surface and black backdrop, a single dramatic spotlight from above, deep shadows, minimalist premium mood."),
			new Style("gold", PREMIUM, "Ánh vàng sang trọng", "Trang sức, quà tặng, dòng cao cấp",
					"Glossy champagne gold reflective surface, warm golden bokeh lights in the background, glamorous evening luxury mood."),
			new Style("velvet", PREMIUM, "Nhung đỏ cổ điển", "Nước hoa, son, set quà",
					"Deep burgundy velvet drapery backdrop and a velvet pedestal, warm moody light, classic opulent perfume advertising."),

			new Style("tet", SEASONAL, "Tết Nguyên Đán", "Chiến dịch Tết, hộp quà biếu",
					"Vietnamese Lunar New Year setting with red and gold decor, yellow apricot blossoms and red envelopes, warm festive light, no text, no letters."),
			new Style("christmas", SEASONAL, "Giáng sinh", "Mùa Noel cuối năm",
					"Cozy Christmas setting with pine branches, warm fairy light bokeh, red and gold ornaments, light snow, no text."),
			new Style("valentine", SEASONAL, "Lãng mạn", "Quà tặng 14/2, 8/3, 20/10",
					"Romantic setting with red roses, scattered rose petals and soft pink bokeh, warm gentle light, no text."),
			new Style("sale", SEASONAL, "Mega Sale", "Ngày đôi 9.9, 10.10, 11.11, 12.12",
					"Vibrant orange and pink backdrop with floating confetti, gift boxes and balloons, energetic online shopping festival mood, no text, no letters, no numbers."));

	static final List<Style> CREATIVE_SCENES = SCENES.stream()
			.map(s -> new Style(s.id(), s.group(), s.label(), s.hint(), CREATIVE_PREFIX + s.prompt() + CREATIVE_SUFFIX))
			.toList();

	static final List<Style> MOTIONS = List.of(
			new Style("showcase", CAMERA, "Xoay giới thiệu", "Giới thiệu sản phẩm mới",
					"Slow cinematic camera orbit around the product, soft studio lighting, gentle light sweep across the packaging, premium commercial."),
			new Style("pushin", CAMERA, "Cận cảnh chi tiết", "Khoe nhãn, chất liệu, bao bì",
					"Slow smooth camera push-in from a medium shot to a close-up of the product, shallow depth of field, soft studio light, premium commercial."),
			new Style("lightsweep", CAMERA, "Ánh sáng lướt", "Ra mắt sản phẩm, dòng cao cấp",
					"Static camera, a soft beam of light slowly sweeps across the still product revealing its shape, dark background, elegant reveal."),
			new Style("splash", EFFECTS, "Hiệu ứng nước", "Skincare, sản phẩm cấp ẩm",
					"Product stands still while fresh water splashes around it in slow motion, bright clean beauty commercial."),
			new Style("fabric", EFFECTS, "Vải bay chuyển động", "Thời trang, nước hoa",
					"Flowing silk fabric moves gracefully in the breeze, soft light, elegant fashion commercial, smooth camera push-in."),
			new Style("petals", EFFECTS, "Cánh hoa rơi", "Mỹ phẩm, quà tặng lãng mạn",
					"Delicate flower petals fall gently around the still product in slow motion, soft pastel light, romantic beauty commercial."),
			new Style("mist", EFFECTS, "Sương khói huyền ảo", "Nước hoa, tinh dầu",
					"Soft mist drifts slowly around the still product, moody backlight, mysterious premium perfume commercial."),
			new Style("festive", EFFECTS, "Lấp lánh lễ hội", "Tết, Noel, mùa sale",
					"Golden sparkles and warm bokeh lights twinkle around the still product, gentle camera push-in, festive holiday commercial."));

	/** Appended to Bria's built-in product-holding instruction. */
	static final List<Style> HOLDING_POSES = List.of(
			new Style("chest", HOLDING, "Cầm trước ngực", "Tự nhiên, hợp ảnh giới thiệu",
					"Hold the product in front of the chest with both hands, label facing the camera."),
			new Style("face", HOLDING, "Cầm cạnh mặt", "Kiểu ảnh review mỹ phẩm",
					"Hold the product up next to the face with one hand, label facing the camera."),
			new Style("show", HOLDING, "Đưa về phía máy", "Khoe sản phẩm, nhãn rõ nhất",
					"Hold the product out towards the camera with one hand, label clearly visible and facing the camera."));

	/** Ids are the garment categories of the FASHN try-on schema. */
	static final List<Style> GARMENT_TYPES = List.of(
			new Style("auto", GARMENT, "Tự nhận diện", "AI tự đoán áo, quần hay váy", "auto"),
			new Style("tops", GARMENT, "Áo", "Áo thun, sơ mi, áo khoác", "tops"),
			new Style("bottoms", GARMENT, "Quần, chân váy", "Quần dài, quần short, chân váy", "bottoms"),
			new Style("one-pieces", GARMENT, "Đồ liền thân", "Váy liền, jumpsuit, đồ bộ", "one-pieces"));

	static final List<Style> LIGHTS = Arrays.stream(Lighting.values())
			.map(l -> new Style(l.getId(), LIGHT, l.getLabel(), l.getHint(), l.getType()))
			.toList();

	private Vibes() {
	}
}
