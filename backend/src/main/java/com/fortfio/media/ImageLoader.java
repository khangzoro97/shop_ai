package com.fortfio.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ImageLoader {

	private final RestClient client = RestClient.create();
	private final FileStorage storage;

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
			return client.get().uri(URI.create(source)).retrieve().body(byte[].class);
		}
		return storage.read(source).orElseThrow(() -> new IllegalArgumentException(
				"Unsupported image source: " + source.substring(0, Math.min(source.length(), 30))));
	}
}
