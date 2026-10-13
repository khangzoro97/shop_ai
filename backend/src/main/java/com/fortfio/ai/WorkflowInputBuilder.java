package com.fortfio.ai;

import java.util.List;
import java.util.Map;

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
							"output_format", "png")),
					new StepRequest(StepKind.BACKDROP, fal.backdropModel(), Map.of(
							"prompt", prompt(workflow, styleId, extraPrompt) + BACKDROP_SUFFIX,
							"image_size", format.getFalImageSize(),
							"num_images", 1)));
			case PRODUCT_PHOTO -> main(fal, workflow, Map.of(
					"prompt", prompt(workflow, styleId, extraPrompt),
					"image_urls", List.of(images.get("product")),
					"num_images", 1));
			case VIRTUAL_TRYON -> main(fal, workflow, Map.of(
					"human_image_url", images.get("person"),
					"garment_image_url", images.get("garment")));
			case PRODUCT_VIDEO -> main(fal, workflow, Map.of(
					"prompt", prompt(workflow, styleId, extraPrompt),
					"image_url", images.get("product"),
					"duration", "5"));
		};
	}

	private static List<StepRequest> main(AppProperties.Fal fal, Workflow workflow, Map<String, Object> input) {
		return List.of(new StepRequest(StepKind.MAIN, fal.modelFor(workflow), input));
	}

	private static void validateImages(Workflow workflow, Map<String, String> images) {
		for (Workflow.ImageSlot slot : workflow.getImageSlots()) {
			String image = images == null ? null : images.get(slot.key());
			if (isBlank(image)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu " + slot.label().toLowerCase());
			}
			if (!image.startsWith("https://") && !image.startsWith("data:image/")) {
				throw new ApiException(HttpStatus.BAD_REQUEST, slot.label() + " phải là link https hoặc ảnh tải lên");
			}
		}
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
