package com.fortfio.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fortfio.config.AppProperties;

/**
 * Downloads files only from allow-listed hosts (AI provider CDNs), without following redirects
 * and with a size cap, so a crafted URL cannot make the server call internal addresses (SSRF).
 */
@Component
public class RemoteFiles {

	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"video/mp4", "mp4",
			"video/webm", "webm");

	public record Download(byte[] data, String extension) {
	}

	private final RestClient client;
	private final List<String> allowedHosts;
	private final long maxBytes;

	public RemoteFiles(AppProperties properties) {
		this.allowedHosts = properties.uploads().allowedRemoteHosts();
		this.maxBytes = properties.uploads().maxDownloadSize().toBytes();
		HttpClient httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(120));
		this.client = RestClient.builder().requestFactory(requestFactory).build();
	}

	public boolean isAllowed(String url) {
		if (!url.startsWith("https://")) {
			return false;
		}
		try {
			return isAllowedHost(URI.create(url).getHost(), allowedHosts);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	public Download download(String url) {
		if (!isAllowed(url)) {
			throw new IllegalArgumentException("Download host not allowed: " + url);
		}
		return client.get().uri(URI.create(url)).exchange((request, response) -> {
			if (!response.getStatusCode().is2xxSuccessful()) {
				throw new IllegalStateException("Download failed with HTTP " + response.getStatusCode().value());
			}
			if (response.getHeaders().getContentLength() > maxBytes) {
				throw new IllegalStateException("Remote file exceeds " + maxBytes + " bytes");
			}
			MediaType type = response.getHeaders().getContentType();
			return new Download(readCapped(response.getBody()), extension(type, url));
		});
	}

	private byte[] readCapped(InputStream body) {
		try (body) {
			byte[] data = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
			if (data.length > maxBytes) {
				throw new IllegalStateException("Remote file exceeds " + maxBytes + " bytes");
			}
			return data;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String extension(MediaType type, String url) {
		if (type != null) {
			String known = EXTENSIONS.get(type.getType() + "/" + type.getSubtype());
			if (known != null) {
				return known;
			}
		}
		String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
		String ext = path.substring(path.lastIndexOf('.') + 1);
		return EXTENSIONS.containsValue(ext) ? ext : "bin";
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
