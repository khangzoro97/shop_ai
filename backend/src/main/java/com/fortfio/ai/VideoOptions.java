package com.fortfio.ai;

import java.util.List;

/**
 * Length, sharpness and soundtrack of a product video.
 *
 * @param music null for a silent video
 */
public record VideoOptions(int seconds, VideoResolution resolution, MusicMood music) {

	public static final int MIN_SECONDS = 4;
	public static final int MAX_SECONDS = 30;
	public static final List<Integer> PRESET_SECONDS = List.of(5, 10, 20, 30);
	/** The music model bills each started minute (about 0.02 USD). */
	public static final int MUSIC_CREDITS = 1;

	public static final VideoOptions DEFAULT = new VideoOptions(5, VideoResolution.P720, null);

	public int credits() {
		return seconds * resolution.getCreditsPerSecond() + (music == null ? 0 : MUSIC_CREDITS);
	}

	/** Fills in defaults for options the customer left out. */
	public static VideoOptions normalize(VideoOptions options) {
		if (options == null) {
			return DEFAULT;
		}
		return new VideoOptions(
				options.seconds() <= 0 ? DEFAULT.seconds() : options.seconds(),
				options.resolution() == null ? DEFAULT.resolution() : options.resolution(),
				options.music());
	}
}
