package com.fortfio.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

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
	/** Front-facing on purpose: when a clip turns the product, the AI invents the back label. */
	private static final String CONTINUE_PROMPT = "Continue the shot smoothly with a subtle slow camera movement and"
			+ " soft shifting light. The product stays in place with its label facing the camera; its shape and label"
			+ " text stay unchanged and readable.";
	private static final String PLANNED_CLIP_SUFFIX = " One continuous shot with no cuts or scene changes, starting"
			+ " exactly from the given frame and keeping the same setting and props. The product's shape and label text"
			+ " stay unchanged.";

	private static final double MIN_UPSCALE_FACTOR = 1.2;
	private static final double MAX_UPSCALE_FACTOR = 4;

	private static final int VIDU_MAX_CLIP_SECONDS = 16;
	private static final int SEEDANCE_MAX_CLIP_SECONDS = 12;
	/** The music model renders at least 10 seconds; the music is cut to the video length. */
	private static final int MIN_MUSIC_SECONDS = 10;
	/** Photos this close to the video aspect ratio are used as-is (the model crops the difference). */
	private static final double ASPECT_TOLERANCE = 0.03;

	private static final Pattern IMAGE_DATA_URL = Pattern.compile("data:image/(jpeg|png|webp);base64,");
	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
	private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
	private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

	private final AppProperties properties;
	private final PromptWriter promptWriter;

	public record StepRequest(StepKind kind, String model, Map<String, Object> input) {
	}

	/**
	 * Validates the request and returns the calls to submit right away. In simple mode the
	 * description is turned into a {@link PromptPlan} first, unless the spec already carries one (retry).
	 */
	public List<StepRequest> firstStage(GenerationSpec spec, Map<String, String> images) {
		validate(spec, images);
		if (spec.simpleMode() && spec.plan() == null) {
			return List.of(promptWriter.planStep(spec, images,
					spec.workflow().isVideo() ? clipSeconds(spec) : List.of()));
		}
		return generationStage(spec, images);
	}

	private List<StepRequest> generationStage(GenerationSpec spec, Map<String, String> images) {
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
				String productName = spec.effectiveProductName();
				if (!isBlank(productName)) {
					yield List.of(new StepRequest(StepKind.CUTOUT, fal.guidedCutoutModel(), Map.of(
							"image_url", images.get("product"),
							"prompt", "Keep only the " + productName.trim()
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
			case PRODUCT_HOLDING -> main(fal, workflow, Map.of(
					"person_image_url", images.get("person"),
					"product_image_urls", List.of(images.get("product")),
					"instruction", prompt(spec)));
			case VIRTUAL_TRYON -> fashnTryOn(fal)
					? main(fal, workflow, Map.of(
							"model_image", images.get("person"),
							"garment_image", images.get("garment"),
							"category", style(spec).prompt(),
							"mode", "quality",
							"num_samples", spec.variants(),
							"output_format", "jpeg"))
					: main(fal, workflow, Map.of(
							"human_image_url", images.get("person"),
							"garment_image_url", images.get("garment")));
			case PRODUCT_VIDEO -> {
				List<StepRequest> first = new ArrayList<>();
				String product = images.get("product");
				OutputFormat format = spec.format();
				if (needsExpanding(product, format)) {
					first.add(new StepRequest(StepKind.EXPAND, fal.video().expandModel(), Map.of(
							"image_url", product,
							"aspect_ratio", format.getRatio(),
							"canvas_size", List.of(format.getWidth(), format.getHeight()))));
				} else {
					first.add(clip(fal, spec, 0, product));
				}
				MusicMood music = spec.effectiveMusic();
				if (music != null) {
					first.add(new StepRequest(StepKind.MUSIC, fal.video().musicModel(), Map.of(
							"prompt", music.getPrompt(),
							"duration", Math.max(MIN_MUSIC_SECONDS, spec.video().seconds() + 1))));
				}
				yield first;
			}
			case RELIGHT -> {
				Lighting lighting = Lighting.byId(style(spec).id()).orElseThrow();
				yield main(fal, workflow, Map.of(
						"image_url", images.get("image"),
						"light_direction", lighting.getDirection(),
						"light_type", lighting.getType()));
			}
			case UPSCALE_2K, UPSCALE_4K -> main(fal, workflow, Map.of(
					"image_url", images.get("image"),
					"upscale_factor", upscaleFactor(workflow, images.get("image")),
					"model", "High Fidelity V3",
					"output_format", "jpeg"));
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
		if (outputs.keySet().equals(Set.of(StepKind.PROMPT))) {
			return generationStage(spec, images);
		}
		AppProperties.Fal fal = properties.fal();
		return switch (spec.workflow()) {
			case PRODUCT_EXACT -> outputs.containsKey(StepKind.CUTOUT) && !outputs.containsKey(StepKind.MAIN)
					&& !fal.compositeEngine()
							? List.of(productShot(fal, spec, images, outputs.get(StepKind.CUTOUT).getFirst()))
							: List.of();
			case PRODUCT_PHOTO, PRODUCT_HOLDING, RELIGHT -> labelChecksOnce(fal, images.get(labelSource(spec.workflow())), outputs);
			case VIRTUAL_TRYON -> labelChecksOnce(fal, images.get("garment"), outputs);
			case PRODUCT_VIDEO -> nextVideoStage(fal, spec, outputs);
			// Upscaled results are too large to send back for OCR, and small text was not legible before either.
			case UPSCALE_2K, UPSCALE_4K -> List.of();
		};
	}

	/**
	 * Long videos are rendered as clips, each starting from the last frame of the one before, then
	 * joined; the music is laid under the finished video. Whichever call produces the final video is
	 * the MAIN step.
	 */
	private List<StepRequest> nextVideoStage(AppProperties.Fal fal, GenerationSpec spec,
			Map<StepKind, List<String>> outputs) {
		if (outputs.containsKey(StepKind.MAIN)) {
			return List.of();
		}
		int clipCount = clipSeconds(spec).size();
		List<String> clips = outputs.getOrDefault(StepKind.CLIP, List.of());
		List<String> frames = outputs.getOrDefault(StepKind.LAST_FRAME, List.of());
		if (clips.isEmpty()) {
			return List.of(clip(fal, spec, 0, outputs.get(StepKind.EXPAND).getFirst()));
		}
		if (clips.size() < clipCount) {
			return frames.size() < clips.size()
					? List.of(new StepRequest(StepKind.LAST_FRAME, fal.video().lastFrameModel(), Map.of(
							"video_url", clips.getLast(),
							"frame_type", "last")))
					: List.of(clip(fal, spec, clips.size(), frames.getLast()));
		}
		boolean music = spec.effectiveMusic() != null;
		if (clipCount > 1 && !outputs.containsKey(StepKind.JOIN)) {
			return List.of(new StepRequest(music ? StepKind.JOIN : StepKind.MAIN, fal.video().joinModel(),
					Map.of("video_urls", clips)));
		}
		String video = clipCount > 1 ? outputs.get(StepKind.JOIN).getFirst() : clips.getFirst();
		return List.of(new StepRequest(StepKind.MAIN, fal.video().addMusicModel(), Map.of(
				"video_url", video,
				"audio_url", outputs.get(StepKind.MUSIC).getFirst())));
	}

	private StepRequest clip(AppProperties.Fal fal, GenerationSpec spec, int index, String firstFrame) {
		List<Integer> lengths = clipSeconds(spec);
		boolean last = lengths.size() == 1 && spec.effectiveMusic() == null;
		String model = fal.modelFor(Workflow.PRODUCT_VIDEO);
		String planned = spec.plan() == null ? null : spec.plan().clip(index, lengths.size());
		String prompt = planned != null ? planned + PLANNED_CLIP_SUFFIX
				: index == 0 ? prompt(spec)
				: withUserPrompt(CONTINUE_PROMPT, spec);
		int seconds = lengths.get(index);
		VideoResolution resolution = spec.video().resolution();
		Map<String, Object> input = model.contains("vidu")
				? Map.of(
						"prompt", prompt,
						"image_url", firstFrame,
						"duration", seconds,
						"resolution", resolution.getLabel(),
						"audio", false)
				: Map.of(
						"prompt", prompt,
						"image_url", firstFrame,
						"duration", String.valueOf(seconds),
						// Seedance starts at 480p.
						"resolution", resolution == VideoResolution.P360 ? "480p" : resolution.getLabel(),
						"aspect_ratio", spec.format().getRatio(),
						"generate_audio", false);
		return new StepRequest(last ? StepKind.MAIN : StepKind.CLIP, model, input);
	}

	/** Splits the length into as few clips as the model allows, of near-equal length. */
	public List<Integer> clipSeconds(GenerationSpec spec) {
		return clipSeconds(spec.video().seconds());
	}

	public List<Integer> clipSeconds(int total) {
		String model = properties.fal().modelFor(Workflow.PRODUCT_VIDEO);
		int max = model.contains("vidu") ? VIDU_MAX_CLIP_SECONDS : SEEDANCE_MAX_CLIP_SECONDS;
		int count = (total + max - 1) / max;
		List<Integer> lengths = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			lengths.add(total / count + (i < total % count ? 1 : 0));
		}
		return lengths;
	}

	private static boolean needsExpanding(String dataUrl, OutputFormat format) {
		int[] size = imageSize(dataUrl);
		double ratio = (double) size[0] / size[1];
		double target = (double) format.getWidth() / format.getHeight();
		return Math.abs(ratio - target) / target > ASPECT_TOLERANCE;
	}

	/** The local compositing engine and the Kolors try-on render a single result. */
	public int maxVariants(Workflow workflow) {
		boolean single = (workflow.preservesProduct() && properties.fal().compositeEngine())
				|| (workflow == Workflow.VIRTUAL_TRYON && !fashnTryOn(properties.fal()));
		return single ? 1 : workflow.getMaxVariants();
	}

	private static String labelSource(Workflow workflow) {
		return workflow.enhancesResult() ? "image" : "product";
	}

	private static boolean fashnTryOn(AppProperties.Fal fal) {
		return fal.modelFor(Workflow.VIRTUAL_TRYON).contains("fashn");
	}

	/** Target long side in pixels; the factor is derived from the image so the price stays bounded. */
	private static double upscaleFactor(Workflow workflow, String dataUrl) {
		int target = workflow == Workflow.UPSCALE_4K ? 4096 : 2048;
		int[] size = imageSize(dataUrl);
		int longSide = Math.max(size[0], size[1]);
		double factor = Math.min(MAX_UPSCALE_FACTOR, (double) target / longSide);
		if (factor < MIN_UPSCALE_FACTOR) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Ảnh đã đạt độ phân giải này, không cần phóng to thêm");
		}
		return Math.round(factor * 100) / 100.0;
	}

	/** Width and height, read from the image header only. */
	private static int[] imageSize(String dataUrl) {
		byte[] data = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = in == null ? Collections.emptyIterator() : ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được kích thước ảnh");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				return new int[] {reader.getWidth(0), reader.getHeight(0)};
			} finally {
				reader.dispose();
			}
		} catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được kích thước ảnh");
		}
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
		Placement placement = spec.effectivePlacement() == null ? Placement.BOTTOM_CENTER : spec.effectivePlacement();
		input.put("placement_type", "manual_placement");
		input.put("manual_placement_selection", placement.getBriaValue());
		input.put("shot_size", List.of(spec.format().getWidth(), spec.format().getHeight()));
		input.put("num_results", spec.variants());
		input.put("fast", false);
		return new StepRequest(StepKind.MAIN, fal.productShotModel(), input);
	}

	/** OCR of the source and of every result, submitted once after the results are in. */
	private static List<StepRequest> labelChecksOnce(AppProperties.Fal fal, String source,
			Map<StepKind, List<String>> outputs) {
		if (outputs.containsKey(StepKind.QC_SOURCE)) {
			return List.of();
		}
		List<StepRequest> checks = new ArrayList<>();
		checks.add(new StepRequest(StepKind.QC_SOURCE, fal.ocrModel(), Map.of("image_url", source)));
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
		VideoOptions video = spec.video();
		if (workflow.isVideo() && (video == null || video.resolution() == null || spec.format() == null
				|| video.seconds() < VideoOptions.MIN_SECONDS || video.seconds() > VideoOptions.MAX_SECONDS)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Thời lượng video phải từ " + VideoOptions.MIN_SECONDS
					+ " đến " + VideoOptions.MAX_SECONDS + " giây");
		}
		if (spec.brief() != null && spec.brief().length() > PromptWriter.MAX_BRIEF_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Mô tả tối đa " + PromptWriter.MAX_BRIEF_LENGTH + " ký tự");
		}
		for (Workflow.ImageSlot slot : workflow.getImageSlots()) {
			String image = images == null ? null : images.get(slot.key());
			if (isBlank(image)) {
				if (slot.required()) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu " + slot.label().toLowerCase());
				}
				continue;
			}
			validateImage(slot.label(), image);
		}
		Set<String> slotKeys = workflow.getImageSlots().stream().map(Workflow.ImageSlot::key).collect(Collectors.toSet());
		if (!slotKeys.containsAll(images.keySet())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Ảnh gửi lên không khớp với tính năng đã chọn");
		}
	}

	/** Only uploaded JPG, PNG or WebP data URIs within the size limit. */
	public void validateImage(String label, String image) {
		Matcher header = IMAGE_DATA_URL.matcher(image);
		if (!header.lookingAt()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, label + " phải là ảnh JPG, PNG hoặc WebP tải lên từ máy");
		}
		String base64 = image.substring(header.end());
		if (base64.length() * 3L / 4 > properties.uploads().maxImageSize().toBytes()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					label + " vượt quá " + properties.uploads().maxImageSize().toMegabytes() + "MB");
		}
		if (!hasImageSignature(base64)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, label + " không phải file ảnh hợp lệ");
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

	/** The planned prompt in simple mode; otherwise the vibe's prompt plus the customer's text. */
	private static String prompt(GenerationSpec spec) {
		if (spec.plan() != null && !isBlank(spec.plan().scene())) {
			return spec.plan().scene();
		}
		return withUserPrompt(style(spec).prompt(), spec);
	}

	/** Without a plan (the language model failed), the raw description is appended as before. */
	private static String withUserPrompt(String base, GenerationSpec spec) {
		String text = isBlank(spec.prompt()) ? spec.brief() : spec.prompt();
		return isBlank(text) ? base : base + " " + text.trim();
	}

	private static Workflow.Style style(GenerationSpec spec) {
		Workflow workflow = spec.workflow();
		String styleId = spec.style();
		return isBlank(styleId)
				? workflow.getStyles().getFirst()
				: workflow.findStyle(styleId)
						.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Phong cách không hợp lệ: " + styleId));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
