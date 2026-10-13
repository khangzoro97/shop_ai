package com.fortfio.ai;

import java.util.List;
import java.util.Map;

public interface AiProvider {

	Submission submit(String model, Map<String, Object> input);

	Status status(Submission submission);

	List<String> result(Submission submission);

	record Submission(String requestId, String statusUrl, String responseUrl) {
	}

	enum Status {
		IN_QUEUE, IN_PROGRESS, COMPLETED
	}
}
