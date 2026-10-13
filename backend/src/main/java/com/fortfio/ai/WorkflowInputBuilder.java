package com.fortfio.ai;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;

import lombok.RequiredArgsConstructor;

/**
 * Maps a workflow request to provider calls in the input schema of each configured fal model.
 * Changing a model in application.yml usually requires updating the mapping here too.
 */
@Component
@RequiredArgsConstructor
public class WorkflowInputBuilder {

	private static final String BACKDROP_SUFFIX = " Empty surface in the lower center for a product to be placed later."
			+ " Product photography background plate, no products, no bottles, no people, no text, no logos, photorealistic.";
	private static final String KEEP_PRODUCT_SUFFIX = " Keep the product's shape, label text, logo and colors exactly"
			+ " as in the reference photo.";
	private static final String VIDEO_NEGATIVE_PROMPT = "blur, distort, low quality, warped label, changing text,"
			+ " deformed product, extra products, morphing";

	private static final Pattern IMAGE_DATA_URL = Pattern.compile("data:image/(jpeg|png|webp);base64,");
	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
	private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
	private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

	private final AppProperties properties;

	public record StepRequest(StepKind kind, String model, Map<String, Object> input) {
	}

	public List<StepRequest> build(Workflow workflow, String styleId, String extraPrompt, OutputFormat format,
			Map<String, String> images) {
		validateImages(workflow, images);
		AppProperties.Fal fal = properties.fal();

		return switch (workflow) {
			case PRODUCT_EXACT -> List.of(
					new StepRequest(StepKind.CUTOUT, fal.backgroundRemovalModel(), Map.of(
							"image_url", images.get("product"),
							"model", "General Use (Heavy)",
							"operating_resolution", "2048x2048",
							"output_format", "png")),
					new StepRequest(StepKind.BACKDROP, fal.backdropModel(), Map.of(
							"prompt", prompt(workflow, styleId, extraPrompt) + BACKDROP_SUFFIX,
							"image_size", format.falImageSize(),
							"num_images", 1)));
			case PRODUCT_PHOTO -> main(fal, workflow, Map.of(
					"prompt", prompt(workflow, styleId, extraPrompt) + KEEP_PRODUCT_SUFFIX,
					"image_urls", List.of(images.get("product")),
					"output_format", "jpeg",
					"num_images", 1));
			case VIRTUAL_TRYON -> main(fal, workflow, Map.of(
					"human_image_url", images.get("person"),
					"garment_image_url", images.get("garment")));
			case PRODUCT_VIDEO -> main(fal, workflow, Map.of(
					"prompt", prompt(workflow, styleId, extraPrompt),
					"image_url", images.get("product"),
					"negative_prompt", VIDEO_NEGATIVE_PROMPT,
					"duration", "5"));
		};
	}

	private static List<StepRequest> main(AppProperties.Fal fal, Workflow workflow, Map<String, Object> input) {
		return List.of(new StepRequest(StepKind.MAIN, fal.modelFor(workflow), input));
	}

	/** Only uploaded data URIs are accepted, so users cannot make the AI provider fetch arbitrary links. */
	private void validateImages(Workflow workflow, Map<String, String> images) {
		long maxBytes = properties.uploads().maxImageSize().toBytes();
		for (Workflow.ImageSlot slot : workflow.getImageSlots()) {
			String image = images == null ? null : images.get(slot.key());
			if (isBlank(image)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu " + slot.label().toLowerCase());
			}
			Matcher header = IMAGE_DATA_URL.matcher(image);
			if (!header.lookingAt()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, slot.label() + " phải là ảnh JPG, PNG hoặc WebP tải lên từ máy");
			}
			String base64 = image.substring(header.end());
			if (base64.length() * 3L / 4 > maxBytes) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						slot.label() + " vượt quá " + properties.uploads().maxImageSize().toMegabytes() + "MB");
			}
			if (!hasImageSignature(base64)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, slot.label() + " không phải file ảnh hợp lệ");
			}
		}
		Set<String> slotKeys = workflow.getImageSlots().stream().map(Workflow.ImageSlot::key).collect(Collectors.toSet());
		if (!slotKeys.containsAll(images.keySet())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Ảnh gửi lên không khớp với tính năng đã chọn");
		}
	}

	private static boolean hasImageSignature(String base64) {
		byte[] head;
		try {
			head = Base64.getDecoder().decode(base64.substring(0, Math.min(base64.length(), 16)));
		} catch (IllegalArgumentException ex) {
			return false;
		}
		return startsWith(head, 0, JPEG_MAGIC) || startsWith(head, 0, PNG_MAGIC)
				|| (startsWith(head, 0, RIFF_MAGIC) && startsWith(head, 8, WEBP_MAGIC));
	}

	private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
		if (data.length < offset + prefix.length) {
			return false;
		}
		return Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
	}

	private static String prompt(Workflow workflow, String styleId, String extraPrompt) {
		String base = isBlank(styleId)
				? workflow.getStyles().getFirst().prompt()
				: workflow.findStyle(styleId)
						.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Phong cách không hợp lệ: " + styleId))
						.prompt();
		return isBlank(extraPrompt) ? base : base + " " + extraPrompt.trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
