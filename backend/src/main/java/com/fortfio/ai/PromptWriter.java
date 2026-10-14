package com.fortfio.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fortfio.ai.WorkflowInputBuilder.StepRequest;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple mode: the customer describes the result in Vietnamese and a language model writes the
 * prompts. Two calls: {@link #assist} rewrites the description in more detail while the customer is
 * still editing it, and {@link #planStep} turns the final description into a {@link PromptPlan} as
 * the first step of the job.
 *
 * <p>Both prompts forbid describing label text, logos and packaging colors: the model would
 * otherwise invent them and the image model would paint them onto the product.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptWriter {

	public static final int MAX_BRIEF_LENGTH = 3000;

	private static final Set<Workflow> SUPPORTED = EnumSet.of(
			Workflow.PRODUCT_EXACT, Workflow.PRODUCT_PHOTO, Workflow.PRODUCT_HOLDING, Workflow.PRODUCT_VIDEO);

	/** Words for all video scenes together; longer answers no longer fit {@link #MAX_BRIEF_LENGTH}. */
	private static final int VIDEO_SCENE_WORDS = 320;

	private static final String ASSIST_RULES = """

			Giữ đúng ý khách, chỉ bổ sung chi tiết hợp lý và cụ thể (vật gì, chất liệu, màu, ở đâu), tránh từ chung \
			chung như "đẹp", "sang trọng" mà không tả rõ. Nếu khách chưa nhập gì thì tự chọn ý tưởng đẹp, hợp với \
			sản phẩm trong ảnh. Chỉ có một sản phẩm chính, không lặp lại sản phẩm. Không tả chữ, logo, tên thương \
			hiệu hay màu bao bì của sản phẩm; chỉ gọi chung loại sản phẩm (ví dụ "chai serum"). Không viết nội dung \
			phản cảm hay vi phạm pháp luật. Chỉ trả về phần mô tả bằng tiếng Việt, đúng các dòng có nhãn như trên, \
			không giải thích, không dùng markdown (không dùng ** hay #).""";
	private static final String ASSIST_IMAGE = """
			Bạn là đạo diễn hình ảnh, giúp chủ shop online Việt Nam lên ý tưởng ảnh quảng cáo sản phẩm. Viết lại ý \
			của khách thành bản mô tả chi tiết đến mức thợ chụp ảnh dựng lại được, gồm đúng các dòng sau, mỗi dòng \
			bắt đầu bằng nhãn:
			Bối cảnh: không gian cụ thể, thời điểm trong ngày, không khí.
			Bề mặt đặt sản phẩm: chất liệu, màu, kết cấu.
			Đồ vật xung quanh: 3-5 vật cụ thể hợp với sản phẩm, mỗi vật ghi chất liệu, màu và vị trí so với sản \
			phẩm (bên trái, bên phải, phía sau, tiền cảnh); đồ vật nhỏ hơn sản phẩm và không che sản phẩm.
			Ánh sáng: nguồn sáng, hướng chiếu, độ mềm, bóng đổ.
			Tông màu: 2-3 màu chủ đạo hợp với sản phẩm.
			Góc máy và bố cục: góc chụp, khoảng cách, vị trí sản phẩm trong khung, độ mờ hậu cảnh.
			Phong cách: cảm giác tổng thể của bức ảnh.
			Tổng cộng 130-200 từ. Không đưa người hay bàn tay vào ảnh, trừ khi khách yêu cầu.""";
	private static final String ASSIST_HOLDING = """
			Bạn giúp chủ shop online Việt Nam viết mô tả cho ảnh người mẫu cầm sản phẩm. Ảnh đính kèm (nếu có) chỉ \
			là ảnh sản phẩm; ảnh người mẫu khách tải lên riêng, gương mặt, trang phục và bối cảnh giữ theo ảnh đó. \
			Luôn viết mô tả, không nhận xét về ảnh. Chỉ tả, gồm đúng các dòng sau, mỗi dòng bắt đầu bằng nhãn:
			Cách cầm: tay nào cầm, các ngón tay đặt ở đâu trên sản phẩm.
			Vị trí và góc sản phẩm: ngang mặt, ngang ngực..., nghiêng bao nhiêu, mặt trước hướng về máy ảnh.
			Tư thế: dáng người, vai, tay còn lại làm gì.
			Biểu cảm và ánh mắt: nụ cười, nhìn vào máy ảnh hay nhìn sản phẩm.
			Tổng cộng 60-110 từ.""";
	private static final String ASSIST_VIDEO = """
			Bạn là đạo diễn, giúp chủ shop online Việt Nam viết kịch bản video quảng cáo sản phẩm dài %d giây, dựng \
			từ một ảnh sản phẩm. Viết theo đúng thứ tự sau:
			Ý tưởng: một câu về thông điệp và cảm xúc chính của video.
			Bối cảnh: không gian, bề mặt đặt sản phẩm, 3-4 đồ vật xung quanh (chất liệu, màu, vị trí), ánh sáng, \
			tông màu; bối cảnh giữ nguyên suốt video.
			%s
			Mỗi cảnh là một cú máy liền mạch: không cắt cảnh, không đổi bối cảnh, không thêm người hay sản phẩm \
			khác trừ khi khách yêu cầu. Cảnh 1 bắt đầu từ đúng ảnh sản phẩm, cảnh sau nối tiếp tự nhiên từ khung \
			cuối của cảnh trước. Sản phẩm đứng yên, mặt trước luôn hướng về máy quay; chỉ máy quay, ánh sáng và \
			hiệu ứng chuyển động. Máy quay luôn ở phía trước sản phẩm, không vòng sang hai bên hay ra phía sau.""";

	private static final String PLAN_RULES = """
			You turn a Vietnamese shop owner's description into settings for an AI product %s. Reply with JSON only.
			Rules:
			- Write prompts in English, concrete and visual. Keep everything the owner asked for; resolve \
			contradictions sensibly. If the description is empty, choose a fitting premium idea for the product \
			in the image.
			- Never describe the product's label text, logo, brand name or packaging colors; call it by its \
			generic type (e.g. "the serum bottle"). The product must stay unchanged.
			- Ignore requests for sexual, violent or illegal content.
			Fields:
			"productName": short generic English name of the product in the image, e.g. "white serum bottle \
			with dropper".
			"summary": one short Vietnamese sentence (max 25 words) telling the owner what will be created.
			""";
	private static final String PLAN_SCENE = """
			"placement": "BOTTOM_CENTER", "BOTTOM_LEFT" or "BOTTOM_RIGHT": where the product sits (default \
			BOTTOM_CENTER).
			"scene": the scene around the product (max 100 words): setting, surface, 2-5 specific props with \
			material, color and position relative to the product, lighting direction, colors, camera angle. Keep \
			every prop and detail the owner described. A single product, no people or hands unless the owner \
			asked. Do not describe the product itself, it is inserted from the photo.
			""";
	private static final String PLAN_CREATIVE = """
			"scene": the full photo (max 100 words): setting, surface, 2-5 specific props with material, color and \
			position relative to the product, lighting direction, colors, camera angle, with a single product as \
			the hero. Keep every prop and detail the owner described.
			""";
	private static final String PLAN_HOLDING = """
			"scene": instruction (max 50 words) for how the person in the photo holds the product: hand, \
			position, tilt, pose, expression. Their face, outfit and background stay unchanged.
			""";
	private static final String PLAN_VIDEO = """
			"music": one of "UPBEAT", "CHILL", "LUXURY", "CUTE", "FESTIVE", "CINEMATIC" fitting the mood.
			"clips": array of exactly %d English strings, one per consecutive clip (%s seconds). Follow the \
			owner's script and timeline: each clip covers its scene, with its timed beats in order and seconds \
			counted from the start of that clip (e.g. "0-3s: slow push-in..., 3-5s: ..."). Each beat gives camera \
			movement, light and effects. Every clip is one continuous shot with no cuts, starting exactly from its \
			first frame (clip 1: the product photo; later clips: the last frame of the previous clip), in the same \
			setting with the same props. No new people or products unless the owner asked. The product stays \
			still with its label facing the camera; the camera stays in front of it and never orbits to its \
			sides or back (turn "orbit" requests into a gentle arc of a few degrees). Max 90 words each.
			""";

	private final AppProperties properties;
	private final AiProvider aiProvider;

	public static boolean supports(Workflow workflow) {
		return SUPPORTED.contains(workflow);
	}

	/**
	 * Rewrites the customer's description in more detail, in Vietnamese, for them to edit.
	 *
	 * @param image       product photo as a data URI, or null
	 * @param clipSeconds clip lengths for videos (one scene per clip)
	 */
	public String assist(Workflow workflow, String text, String image, List<Integer> clipSeconds) {
		String system = switch (workflow) {
			case PRODUCT_HOLDING -> ASSIST_HOLDING;
			case PRODUCT_VIDEO -> ASSIST_VIDEO.formatted(clipSeconds.stream().mapToInt(Integer::intValue).sum(),
					scenes(clipSeconds));
			default -> ASSIST_IMAGE;
		} + ASSIST_RULES;
		AppProperties.Llm llm = properties.fal().llm();
		Map<String, Object> input = input(system, "Ý của khách: " + orEmpty(text, "(chưa nhập)"), image, 0.7);
		String output;
		try {
			output = aiProvider.run(image == null ? llm.textEndpoint() : llm.visionEndpoint(), input).text();
		} catch (RuntimeException ex) {
			log.warn("Prompt assist failed for {}", workflow, ex);
			output = null;
		}
		String cleaned = clean(output);
		if (cleaned.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI chưa viết được, bạn vẫn có thể tạo với mô tả hiện tại");
		}
		return cleaned.substring(0, Math.min(cleaned.length(), MAX_BRIEF_LENGTH));
	}

	/** First job step: turns the final description into a {@link PromptPlan} (read by {@link PromptPlan#parse}). */
	public StepRequest planStep(GenerationSpec spec, Map<String, String> images, List<Integer> clipSeconds) {
		String fields = switch (spec.workflow()) {
			case PRODUCT_EXACT -> PLAN_SCENE;
			case PRODUCT_PHOTO -> PLAN_CREATIVE;
			case PRODUCT_HOLDING -> PLAN_HOLDING;
			case PRODUCT_VIDEO -> PLAN_VIDEO.formatted(clipSeconds.size(),
					String.join(", ", clipSeconds.stream().map(String::valueOf).toList()));
			default -> throw new IllegalArgumentException("No simple mode for " + spec.workflow());
		};
		String system = PLAN_RULES.formatted(spec.workflow().isVideo() ? "video" : "photo") + fields;
		Map<String, Object> input = input(system,
				"Owner's description (Vietnamese): " + orEmpty(spec.brief(), "(empty)"), images.get("product"), 0.4);
		return new StepRequest(StepKind.PROMPT, properties.fal().llm().visionEndpoint(), input);
	}

	private Map<String, Object> input(String system, String prompt, String image, double temperature) {
		Map<String, Object> input = new HashMap<>();
		input.put("model", properties.fal().llm().model());
		input.put("system_prompt", system);
		input.put("prompt", prompt);
		input.put("temperature", temperature);
		input.put("max_tokens", 3000);
		if (image != null) {
			input.put("image_urls", List.of(image));
		}
		return input;
	}

	/** Scene headers matching the clips, so each scene of the script becomes one clip prompt. */
	private static String scenes(List<Integer> clipSeconds) {
		List<String> headers = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < clipSeconds.size(); i++) {
			int end = start + clipSeconds.get(i);
			headers.add("\"Cảnh " + (i + 1) + " (" + start + "–" + end + " giây): <tên cảnh>\"");
			start = end;
		}
		int words = Math.clamp(VIDEO_SCENE_WORDS / clipSeconds.size(), 35, 90);
		return "Sau đó viết đúng " + clipSeconds.size() + " cảnh nối tiếp, mỗi cảnh mở đầu bằng dòng "
				+ String.join(", ", headers) + ". Dưới mỗi dòng đó là timeline 2-3 mốc giây liên tiếp, mỗi mốc một"
				+ " dòng bắt đầu bằng \"- \" và khoảng giây trong video (ví dụ \"- 0–3 giây: máy quay đẩy chậm vào"
				+ " sản phẩm, tia nắng lướt qua\"), tả chuyển động máy quay (đẩy vào, kéo ra, lia nhẹ, nâng lên,"
				+ " hạ xuống...), ánh sáng và hiệu ứng (cánh hoa rơi, hơi nước, tia nắng, giọt nước...). Khoảng "
				+ words + " từ mỗi cảnh.";
	}

	/** Drops markdown and quotes the model sometimes wraps its answer in. */
	private static String clean(String output) {
		if (output == null) {
			return "";
		}
		String text = output.replace("```", "").replace("**", "").trim();
		if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
			text = text.substring(1, text.length() - 1).trim();
		}
		return text;
	}

	private static String orEmpty(String value, String placeholder) {
		return value == null || value.isBlank() ? placeholder : value.trim();
	}
}
