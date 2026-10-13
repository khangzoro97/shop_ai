package com.fortfio.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fortfio.config.AppProperties;

/**
 * Remote images are only downloaded from allow-listed hosts (AI provider CDNs) and redirects
 * are not followed, so a crafted URL cannot make the server call internal addresses (SSRF).
 */
@Component
public class ImageLoader {

	private final RestClient client;
	private final FileStorage storage;
	private final List<String> allowedHosts;

	public ImageLoader(FileStorage storage, AppProperties properties) {
		this.storage = storage;
		this.allowedHosts = properties.uploads().allowedRemoteHosts();
		HttpClient httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(60));
		this.client = RestClient.builder().requestFactory(requestFactory).build();
	}

	public BufferedImage load(String source) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(fetch(source)));
			if (image == null) {
				throw new IllegalStateException("Unsupported image format (PNG or JPEG expected)");
			}
			return image;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private byte[] fetch(String source) {
		if (source.startsWith("data:")) {
			return Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
		}
		if (source.startsWith("https://")) {
			URI uri = URI.create(source);
			if (!isAllowedHost(uri.getHost(), allowedHosts)) {
				throw new IllegalArgumentException("Image host not allowed: " + uri.getHost());
			}
			return client.get().uri(uri).retrieve().body(byte[].class);
		}
		return storage.read(source).orElseThrow(() -> new IllegalArgumentException(
				"Unsupported image source: " + source.substring(0, Math.min(source.length(), 30))));
	}

	static boolean isAllowedHost(String host, List<String> allowedHosts) {
		if (host == null) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		return allowedHosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).anyMatch(allowed ->
				allowed.startsWith("*.") ? normalized.endsWith(allowed.substring(1)) : normalized.equals(allowed));
	}
}
