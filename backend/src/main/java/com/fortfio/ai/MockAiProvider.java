package com.fortfio.ai;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

/**
 * Used when no FAL_KEY is configured, so the full flow can be developed without API cost.
 * Background removal echoes the input image, so composites show the uploaded photo as-is.
 */
public class MockAiProvider implements AiProvider {

	private static final Duration FAKE_LATENCY = Duration.ofSeconds(4);
	private static final String SAMPLE_IMAGE = "https://images.unsplash.com/photo-1620916566398-39f1143ab7be?w=1024";
	private static final String SAMPLE_BACKDROP = gradientBackdrop();
	private static final String SAMPLE_VIDEO = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";

	private static final String SAMPLE_LABEL_TEXT = "SAMPLE LABEL 100ML";

	private final Map<String, Instant> startedAt = new ConcurrentHashMap<>();
	private final Map<String, Result> results = new ConcurrentHashMap<>();

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
	public Result result(Submission submission) {
		startedAt.remove(submission.requestId());
		Result result = results.remove(submission.requestId());
		return result == null ? Result.ofUrls(List.of()) : result;
	}

	private static Result fakeResult(String model, Map<String, Object> input) {
		if (model.contains("ocr")) {
			return new Result(List.of(), SAMPLE_LABEL_TEXT);
		}
		if (model.contains("video")) {
			return Result.ofUrls(List.of(SAMPLE_VIDEO));
		}
		if (model.contains("product-shot") && input.get("image_url") instanceof String image) {
			int count = input.get("num_results") instanceof Integer n ? n : 1;
			return Result.ofUrls(Collections.nCopies(count, image));
		}
		if ((model.contains("birefnet") || model.contains("remove-background")) && input.get("image_url") instanceof String image) {
			return Result.ofUrls(List.of(image));
		}
		if (input.containsKey("image_size")) {
			return Result.ofUrls(List.of(SAMPLE_BACKDROP));
		}
		int count = input.get("num_images") instanceof Integer n ? n : 1;
		return Result.ofUrls(Collections.nCopies(count, SAMPLE_IMAGE));
	}

	/** Generated locally: the server only downloads images from allow-listed AI hosts. */
	private static String gradientBackdrop() {
		int size = 1024;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0xFDE2EF), 0, size, new Color(0xD9CCFF)));
		g.fillRect(0, 0, size, size);
		g.dispose();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "jpg", out);
			return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
