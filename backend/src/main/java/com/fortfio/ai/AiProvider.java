package com.fortfio.ai;

import java.util.List;
import java.util.Map;

public interface AiProvider {

	Submission submit(String model, Map<String, Object> input);

	Status status(Submission submission);

	Result result(Submission submission);

	record Submission(String requestId, String statusUrl, String responseUrl) {
	}

	/**
	 * @param urls media produced by the model, in output order
	 * @param text text output (OCR models), or null
	 */
	record Result(List<String> urls, String text) {

		public static Result ofUrls(List<String> urls) {
			return new Result(urls, null);
		}
	}

	enum Status {
		IN_QUEUE, IN_PROGRESS, COMPLETED
	}
}
