package com.fortfio.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fortfio.common.ApiException;

/**
 * Fixed-window counter kept in memory: limits are per server instance and reset on restart.
 * Move to Redis (e.g. Bucket4j) before running more than one backend instance.
 */
@Component
public class RateLimiter {

	private static final int CLEANUP_THRESHOLD = 10_000;

	private record Window(long resetAtMillis, int count) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public void check(String key, int limit, Duration period, String message) {
		long now = System.currentTimeMillis();
		Window window = windows.compute(key, (k, current) -> current == null || now >= current.resetAtMillis()
				? new Window(now + period.toMillis(), 1)
				: new Window(current.resetAtMillis(), current.count() + 1));
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(w -> now >= w.resetAtMillis());
		}
		if (window.count() > limit) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
		}
	}
}
