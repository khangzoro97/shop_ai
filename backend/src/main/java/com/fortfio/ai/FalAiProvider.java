package com.fortfio.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * fal.ai queue API: https://docs.fal.ai/model-endpoints/queue
 * Status and result URLs must be taken from the submit response: for nested model ids
 * they do not follow the submit path.
 */
public class FalAiProvider implements AiProvider {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};

	private final RestClient client;
	private final String queueUrl;

	public FalAiProvider(String apiKey, String queueUrl) {
		this.queueUrl = queueUrl;
		this.client = RestClient.builder()
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
		Map<String, Object> body = client.get()
				.uri(submission.responseUrl())
				.retrieve()
				.body(JSON_MAP);
		List<String> urls = new ArrayList<>();
		collectUrls(body, urls);
		return new Result(urls, body.get("results") instanceof String text ? text : null);
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
