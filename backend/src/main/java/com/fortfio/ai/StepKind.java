package com.fortfio.ai;

public enum StepKind {
	/** Single-call workflows: the provider output is the final result. */
	MAIN,
	/** Product with transparent background. */
	CUTOUT,
	/** Empty scene the cutout is composited onto. */
	BACKDROP
}
