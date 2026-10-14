package com.fortfio.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Technical settings a language model derived from the customer's description (simple mode).
 * Stored on the job, so a retry reuses them instead of asking the model again.
 *
 * @param productName generic English name of the product, steers background removal
 * @param scene       English prompt for images (for product holding: how the product is held)
 * @param clips       English prompt per video clip, in order
 * @param summary     one Vietnamese sentence telling the customer what the AI understood
 */
@Slf4j
public record PromptPlan(
		String productName,
		Placement placement,
		MusicMood music,
		String summary,
		String scene,
		List<String> clips) {

	/** Used when the model failed: the job falls back to the default prompt plus the description. */
	public static final PromptPlan EMPTY = new PromptPlan(null, null, null, null, null, List.of());

	private static final int MAX_PRODUCT_NAME = 80;
	private static final int MAX_SUMMARY = 300;
	private static final int MAX_PROMPT = 1200;

	private static final JsonMapper JSON = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	public PromptPlan {
		clips = clips == null ? List.of() : List.copyOf(clips);
	}

	/** Clip prompts only count when the model wrote one for every clip. */
	public String clip(int index, int clipCount) {
		return clips.size() == clipCount ? clips.get(index) : null;
	}

	public String toJson() {
		return JSON.writeValueAsString(this);
	}

	public static PromptPlan fromJson(String json) {
		return json == null || json.isBlank() ? null : JSON.readValue(json, PromptPlan.class);
	}

	/** Reads the model's answer; anything unusable becomes {@link #EMPTY} so the job still runs. */
	public static PromptPlan parse(String output) {
		if (output == null) {
			return EMPTY;
		}
		int start = output.indexOf('{');
		int end = output.lastIndexOf('}');
		if (start < 0 || end <= start) {
			log.warn("Prompt plan is not JSON: {}", abbreviate(output, 200));
			return EMPTY;
		}
		Raw raw;
		try {
			raw = JSON.readValue(output.substring(start, end + 1), Raw.class);
		} catch (JacksonException ex) {
			log.warn("Prompt plan could not be read: {}", abbreviate(output, 200), ex);
			return EMPTY;
		}
		List<String> clips = new ArrayList<>();
		for (Object clip : raw.clips() == null ? List.of() : raw.clips()) {
			// Some models answer [{"prompt": "..."}] instead of plain strings.
			Object text = clip instanceof Map<?, ?> map ? map.get("prompt") : clip;
			if (text instanceof String prompt && !prompt.isBlank()) {
				clips.add(abbreviate(prompt.trim(), MAX_PROMPT));
			}
		}
		return new PromptPlan(
				abbreviate(blankToNull(raw.productName()), MAX_PRODUCT_NAME),
				enumOrNull(Placement.class, raw.placement()),
				enumOrNull(MusicMood.class, raw.music()),
				abbreviate(blankToNull(raw.summary()), MAX_SUMMARY),
				abbreviate(blankToNull(raw.scene()), MAX_PROMPT),
				clips);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Raw(String productName, String placement, String music, String summary, String scene,
			List<Object> clips) {
	}

	private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
		if (name == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, name.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String abbreviate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}
}
