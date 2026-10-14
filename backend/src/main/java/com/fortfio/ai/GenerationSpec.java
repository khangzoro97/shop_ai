package com.fortfio.ai;

/**
 * Everything the customer chose for one generation, besides the uploaded images.
 *
 * @param productName optional short description of the product, used to steer background removal
 * @param variants    number of alternative results; credits are charged per variant
 * @param video       only for video workflows
 * @param brief       simple mode: the customer's own description, possibly empty; null in the
 *                    advanced form
 * @param plan        settings the language model derived from the brief, once known
 */
public record GenerationSpec(
		Workflow workflow,
		String style,
		String prompt,
		OutputFormat format,
		Placement placement,
		String productName,
		int variants,
		VideoOptions video,
		String brief,
		PromptPlan plan) {

	public GenerationSpec(Workflow workflow, String style, String prompt, OutputFormat format, Placement placement,
			String productName, int variants) {
		this(workflow, style, prompt, format, placement, productName, variants, null);
	}

	public GenerationSpec(Workflow workflow, String style, String prompt, OutputFormat format, Placement placement,
			String productName, int variants, VideoOptions video) {
		this(workflow, style, prompt, format, placement, productName, variants, video, null, null);
	}

	public boolean simpleMode() {
		return brief != null;
	}

	/** The plan's choice wins over the form's; both may be null. */
	public String effectiveProductName() {
		return plan != null && plan.productName() != null ? plan.productName() : productName;
	}

	public Placement effectivePlacement() {
		return plan != null && plan.placement() != null ? plan.placement() : placement;
	}

	/** Null when the video has no music; in simple mode the AI picks the mood. */
	public MusicMood effectiveMusic() {
		if (video == null || video.music() == null) {
			return null;
		}
		return plan != null && plan.music() != null ? plan.music() : video.music();
	}

	public int credits() {
		return video != null ? video.credits() : workflow.getCredits() * variants;
	}
}
