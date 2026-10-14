package com.fortfio.ai;

public enum StepKind {
	/** Simple mode: a language model turns the customer's description into a {@link PromptPlan}. */
	PROMPT,
	/** The provider output is the final result (one URL per variant). */
	MAIN,
	/** Product with transparent background. */
	CUTOUT,
	/** Empty scene the cutout is composited onto (local compositing engine only). */
	BACKDROP,
	/** Text read from the uploaded product photo. */
	QC_SOURCE,
	/** Text read from one result variant, compared against {@link #QC_SOURCE}. */
	QC_RESULT,
	/** Product photo widened by AI to the video aspect ratio, used as the first frame. */
	EXPAND,
	/** One part of a video longer than the model can render at once; parts are chained in order. */
	CLIP,
	/** Last frame of the previous clip, where the next clip starts. */
	LAST_FRAME,
	/** Background music for the video. */
	MUSIC,
	/** Clips joined into one video, before the music is added. */
	JOIN
}
