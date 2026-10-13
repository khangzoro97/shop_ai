package com.fortfio.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
 *
 * <p>Some workflows run in stages: {@link #firstStage} is submitted when the job is created and
 * {@link #nextStage} is asked for follow-up calls each time all submitted steps have completed.
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

	/** Validates the request and returns the calls to submit right away. */
	public List<StepRequest> firstStage(GenerationSpec spec, Map<String, String> images) {
		validate(spec, images);
		AppProperties.Fal fal = properties.fal();
		Workflow workflow = spec.workflow();

		return switch (workflow) {
			case PRODUCT_EXACT -> {
				if (fal.compositeEngine()) {
					yield List.of(
							automaticCutout(fal, images.get("product")),
							new StepRequest(StepKind.BACKDROP, fal.backdropModel(), Map.of(
									"prompt", prompt(spec) + BACKDROP_SUFFIX,
									"image_size", spec.format().falImageSize(),
									"num_images", 1)));
				}
				// Product shot is always fed a cutout: Bria's own background removal keeps soft props such as fabric.
				if (!isBlank(spec.productName())) {
					yield List.of(new StepRequest(StepKind.CUTOUT, fal.guidedCutoutModel(), Map.of(
							"image_url", images.get("product"),
							"prompt", "Keep only the " + spec.productName().trim()
									+ ". Remove everything else, including props, plants, hands and reflections.",
							"autocrop", true)));
				}
				yield List.of(automaticCutout(fal, images.get("product")));
			}
			case PRODUCT_PHOTO -> main(fal, workflow, Map.of(
					"prompt", prompt(spec) + KEEP_PRODUCT_SUFFIX,
					"image_urls", List.of(images.get("product")),
					"output_format", "jpeg",
					"num_images", spec.variants()));
			case VIRTUAL_TRYON -> main(fal, workflow, Map.of(
					"human_image_url", images.get("person"),
					"garment_image_url", images.get("garment")));
			case PRODUCT_VIDEO -> main(fal, workflow, Map.of(
					"prompt", prompt(spec),
					"image_url", images.get("product"),
					"negative_prompt", VIDEO_NEGATIVE_PROMPT,
					"duration", "5"));
		};
	}

	/**
	 * Follow-up calls once every submitted step has completed; empty when the job is done.
	 *
	 * @param images  uploaded images as data URIs
	 * @param outputs fetchable outputs of the completed steps (one entry per variant for MAIN)
	 */
	public List<StepRequest> nextStage(GenerationSpec spec, Map<String, String> images,
			Map<StepKind, List<String>> outputs) {
		AppProperties.Fal fal = properties.fal();
		return switch (spec.workflow()) {
			case PRODUCT_EXACT -> outputs.containsKey(StepKind.CUTOUT) && !outputs.containsKey(StepKind.MAIN)
					&& !fal.compositeEngine()
							? List.of(productShot(fal, spec, images, outputs.get(StepKind.CUTOUT).getFirst()))
							: List.of();
			case PRODUCT_PHOTO -> outputs.containsKey(StepKind.QC_SOURCE) ? List.of() : labelChecks(fal, images, outputs);
			default -> List.of();
		};
	}

	/** The local compositing engine renders a single result. */
	public int maxVariants(Workflow workflow) {
		return workflow.preservesProduct() && properties.fal().compositeEngine() ? 1 : workflow.getMaxVariants();
	}

	private static StepRequest automaticCutout(AppProperties.Fal fal, String productImage) {
		return new StepRequest(StepKind.CUTOUT, fal.backgroundRemovalModel(), Map.of(
				"image_url", productImage,
				"model", "General Use (Heavy)",
				"operating_resolution", "2048x2048",
				"output_format", "png"));
	}

	private static StepRequest productShot(AppProperties.Fal fal, GenerationSpec spec, Map<String, String> images,
			String productImage) {
		Map<String, Object> input = new HashMap<>();
		input.put("image_url", productImage);
		String scene = images.get("scene");
		if (isBlank(scene)) {
			input.put("scene_description", prompt(spec));
		} else {
			input.put("ref_image_url", scene);
		}
		Placement placement = spec.placement() == null ? Placement.BOTTOM_CENTER : spec.placement();
		input.put("placement_type", "manual_placement");
		input.put("manual_placement_selection", placement.getBriaValue());
		input.put("shot_size", List.of(spec.format().getWidth(), spec.format().getHeight()));
		input.put("num_results", spec.variants());
		input.put("fast", false);
		return new StepRequest(StepKind.MAIN, fal.productShotModel(), input);
	}

	private static List<StepRequest> labelChecks(AppProperties.Fal fal, Map<String, String> images,
			Map<StepKind, List<String>> outputs) {
		List<StepRequest> checks = new ArrayList<>();
		checks.add(new StepRequest(StepKind.QC_SOURCE, fal.ocrModel(), Map.of("image_url", images.get("product"))));
		for (String result : outputs.getOrDefault(StepKind.MAIN, List.of())) {
			checks.add(new StepRequest(StepKind.QC_RESULT, fal.ocrModel(), Map.of("image_url", result)));
		}
		return checks;
	}

	private static List<StepRequest> main(AppProperties.Fal fal, Workflow workflow, Map<String, Object> input) {
		return List.of(new StepRequest(StepKind.MAIN, fal.modelFor(workflow), input));
	}

	/** Only uploaded data URIs are accepted, so users cannot make the AI provider fetch arbitrary links. */
	private void validate(GenerationSpec spec, Map<String, String> images) {
		Workflow workflow = spec.workflow();
		int maxVariants = maxVariants(workflow);
		if (spec.variants() < 1 || spec.variants() > maxVariants) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Số phương án phải từ 1 đến " + maxVariants);
		}
		long maxBytes = properties.uploads().maxImageSize().toBytes();
		for (Workflow.ImageSlot slot : workflow.getImageSlots()) {
			String image = images == null ? null : images.get(slot.key());
			if (isBlank(image)) {
				if (slot.required()) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu " + slot.label().toLowerCase());
				}
				continue;
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

	private static String prompt(GenerationSpec spec) {
		Workflow workflow = spec.workflow();
		String styleId = spec.style();
		String base = isBlank(styleId)
				? workflow.getStyles().getFirst().prompt()
				: workflow.findStyle(styleId)
						.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Phong cách không hợp lệ: " + styleId))
						.prompt();
		return isBlank(spec.prompt()) ? base : base + " " + spec.prompt().trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
