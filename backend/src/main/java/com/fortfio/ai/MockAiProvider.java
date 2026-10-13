package com.fortfio.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used when no FAL_KEY is configured, so the full flow can be developed without API cost.
 * Background removal echoes the input image, so composites show the uploaded photo as-is.
 */
public class MockAiProvider implements AiProvider {

	private static final Duration FAKE_LATENCY = Duration.ofSeconds(4);
	private static final String SAMPLE_IMAGE = "https://images.unsplash.com/photo-1620916566398-39f1143ab7be?w=1024";
	private static final String SAMPLE_BACKDROP = "https://images.unsplash.com/photo-1557683316-973673baf926?w=1200";
	private static final String SAMPLE_VIDEO = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";

	private final Map<String, Instant> startedAt = new ConcurrentHashMap<>();
	private final Map<String, String> results = new ConcurrentHashMap<>();

	@Override
	public Submission submit(String model, Map<String, Object> input) {
		String id = UUID.randomUUID().toString();
		startedAt.put(id, Instant.now());
		results.put(id, fakeResult(model, input));
		return new Submission(id, "mock://" + id + "/status", "mock://" + id);
	}

	@Override
	public Status status(Submission submission) {
		Instant started = startedAt.getOrDefault(submission.requestId(), Instant.EPOCH);
		return Instant.now().isAfter(started.plus(FAKE_LATENCY)) ? Status.COMPLETED : Status.IN_PROGRESS;
	}

	@Override
	public List<String> result(Submission submission) {
		startedAt.remove(submission.requestId());
		String result = results.remove(submission.requestId());
		return result == null ? List.of() : List.of(result);
	}

	private static String fakeResult(String model, Map<String, Object> input) {
		if (model.contains("video")) {
			return SAMPLE_VIDEO;
		}
		if (model.contains("birefnet") && input.get("image_url") instanceof String image) {
			return image;
		}
		if (input.containsKey("image_size")) {
			return SAMPLE_BACKDROP;
		}
		return SAMPLE_IMAGE;
	}
}
