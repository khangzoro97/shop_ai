package com.fortfio.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * fal.ai queue API: https://docs.fal.ai/model-endpoints/queue
 * Status and result URLs must be taken from the submit response: for nested model ids
 * they do not follow the submit path.
 */
public class FalAiProvider implements AiProvider {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};
	/** The customer is waiting on synchronous calls. */
	private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);

	private final RestClient client;
	private final RestClient runClient;
	private final String queueUrl;
	private final String runUrl;

	public FalAiProvider(String apiKey, String queueUrl, String runUrl) {
		this.queueUrl = queueUrl;
		this.runUrl = runUrl;
		this.client = RestClient.builder()
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + apiKey)
				.build();
		SimpleClientHttpRequestFactory runRequests = new SimpleClientHttpRequestFactory();
		runRequests.setConnectTimeout(Duration.ofSeconds(5));
		runRequests.setReadTimeout(RUN_TIMEOUT);
		this.runClient = RestClient.builder()
				.requestFactory(runRequests)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + apiKey)
				.build();
	}

	@Override
	public Submission submit(String model, Map<String, Object> input) {
		Map<String, Object> body = client.post()
				.uri(queueUrl + "/" + model)
				.contentType(MediaType.APPLICATION_JSON)
				.body(input)
				.retrieve()
				.body(JSON_MAP);
		return new Submission(
				(String) body.get("request_id"),
				(String) body.get("status_url"),
				(String) body.get("response_url"));
	}

	@Override
	public Status status(Submission submission) {
		Map<String, Object> body = client.get()
				.uri(submission.statusUrl())
				.retrieve()
				.body(JSON_MAP);
		return Status.valueOf((String) body.get("status"));
	}

	@Override
	public Result result(Submission submission) {
		return toResult(client.get()
				.uri(submission.responseUrl())
				.retrieve()
				.body(JSON_MAP));
	}

	@Override
	public Result run(String model, Map<String, Object> input) {
		return toResult(runClient.post()
				.uri(runUrl + "/" + model)
				.contentType(MediaType.APPLICATION_JSON)
				.body(input)
				.retrieve()
				.body(JSON_MAP));
	}

	/** OCR models answer in "results", language models in "output". */
	private static Result toResult(Map<String, Object> body) {
		List<String> urls = new ArrayList<>();
		collectUrls(body, urls);
		String text = body.get("results") instanceof String results ? results
				: body.get("output") instanceof String output ? output
				: null;
		// Bria edit models return the same file as both "image" and "images".
		return new Result(urls.stream().distinct().toList(), text);
	}

	private static void collectUrls(Object node, List<String> urls) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((key, value) -> {
				if ("url".equals(key) && value instanceof String url) {
					urls.add(url);
				} else {
					collectUrls(value, urls);
				}
			});
		} else if (node instanceof Collection<?> list) {
			list.forEach(item -> collectUrls(item, urls));
		}
	}
}
