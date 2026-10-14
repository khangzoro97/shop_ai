package com.fortfio.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fortfio.config.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Looks at an uploaded photo before any credit is spent and warns about photos the chosen
 * workflow handles badly (ad banners, several products, clothes worn by a person). Advisory only:
 * when the model fails, there are simply no warnings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InputChecker {

	private static final String SYSTEM = """
			You check a product photo uploaded by an online shop before an AI turns it into an ad image. \
			Reply with JSON only, booleans:
			"banner": the photo is an ad banner, poster or screenshot with large added text or graphics, \
			not a plain photo of the product.
			"multipleProducts": more than one separate product is clearly visible (a set in one box counts \
			as one).
			"wornByPerson": the product is clothing or an accessory being worn by a person.
			"person": a person or hand is visible.
			"blurry": the product is blurry or too dark to read its label.
			""";

	private static final JsonMapper JSON = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private final AppProperties properties;
	private final AiProvider aiProvider;

	public record Issue(String code, String message, Workflow suggest) {
	}

	/** Only product and garment photos are checked; people photos are expected to show people. */
	public static boolean checks(Workflow workflow, String slot) {
		return "product".equals(slot) || "garment".equals(slot) && workflow == Workflow.VIRTUAL_TRYON;
	}

	public List<Issue> check(Workflow workflow, String slot, String image) {
		if (!checks(workflow, slot)) {
			return List.of();
		}
		AppProperties.Llm llm = properties.fal().llm();
		Map<String, Object> input = Map.of(
				"model", llm.model(),
				"system_prompt", SYSTEM,
				"prompt", "Check this photo.",
				"temperature", 0,
				"max_tokens", 200,
				"image_urls", List.of(image));
		try {
			return issues(workflow, slot, parse(aiProvider.run(llm.visionEndpoint(), input).text()));
		} catch (RuntimeException ex) {
			log.warn("Input check failed for {}", workflow, ex);
			return List.of();
		}
	}

	static Findings parse(String output) {
		if (output == null) {
			return Findings.NONE;
		}
		int start = output.indexOf('{');
		int end = output.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Findings.NONE;
		}
		try {
			return JSON.readValue(output.substring(start, end + 1), Findings.class);
		} catch (JacksonException ex) {
			log.warn("Input check answer could not be read: {}", output, ex);
			return Findings.NONE;
		}
	}

	static List<Issue> issues(Workflow workflow, String slot, Findings found) {
		List<Issue> issues = new ArrayList<>();
		if (found.banner()) {
			issues.add(new Issue("BANNER", "Ảnh giống banner có chữ quảng cáo. AI sẽ giữ cả phần chữ đó; nên dùng "
					+ "ảnh chụp riêng sản phẩm trên nền trơn.", null));
		}
		if (found.multipleProducts()) {
			issues.add(new Issue("MULTIPLE", "Ảnh có nhiều sản phẩm. AI làm đẹp nhất khi mỗi ảnh chỉ có một sản phẩm.",
					null));
		}
		boolean productOnly = workflow == Workflow.PRODUCT_EXACT || workflow == Workflow.PRODUCT_PHOTO;
		if (productOnly && found.wornByPerson()) {
			issues.add(new Issue("WORN", "Đây là đồ đang mặc trên người. Muốn ảnh người mẫu mặc đồ của shop, hãy "
					+ "dùng Người mẫu, Mặc thử quần áo với ảnh trang phục trải phẳng.", Workflow.VIRTUAL_TRYON));
		} else if (productOnly && found.person()) {
			issues.add(new Issue("PERSON", "Ảnh có người hoặc bàn tay. Nên chụp riêng sản phẩm để AI tách đúng; muốn "
					+ "ảnh người mẫu cầm sản phẩm thì dùng Người mẫu, Cầm sản phẩm.", Workflow.PRODUCT_HOLDING));
		}
		if (found.blurry()) {
			issues.add(new Issue("BLURRY", "Ảnh hơi mờ hoặc tối, chữ trên nhãn có thể không rõ trong kết quả.", null));
		}
		return issues;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Findings(boolean banner, boolean multipleProducts, boolean wornByPerson, boolean person,
			boolean blurry) {

		static final Findings NONE = new Findings(false, false, false, false, false);
	}
}
