package com.fortfio.ai;

public enum StepKind {
	/** The provider output is the final result (one URL per variant). */
	MAIN,
	/** Product with transparent background. */
	CUTOUT,
	/** Empty scene the cutout is composited onto (local compositing engine only). */
	BACKDROP,
	/** Text read from the uploaded product photo. */
	QC_SOURCE,
	/** Text read from one result variant, compared against {@link #QC_SOURCE}. */
	QC_RESULT
}
