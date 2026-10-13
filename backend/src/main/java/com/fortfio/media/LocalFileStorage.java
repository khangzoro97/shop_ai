package com.fortfio.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fortfio.config.AppProperties;

/**
 * Files are served by {@link com.fortfio.config.WebConfig} under /files/**.
 * Replace with an S3/R2 implementation before running more than one backend instance.
 */
@Component
public class LocalFileStorage implements FileStorage {

	private final Path root;
	private final String publicBaseUrl;

	public LocalFileStorage(AppProperties properties) {
		this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
		this.publicBaseUrl = properties.storage().publicBaseUrl();
	}

	public Path root() {
		return root;
	}

	@Override
	public String save(byte[] data, String extension) {
		String name = LocalDate.now() + "/" + UUID.randomUUID() + "." + extension;
		Path target = root.resolve(name);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, data);
		} catch (IOException ex) {
			throw new UncheckedIOException("Cannot store " + name, ex);
		}
		return publicBaseUrl + "/" + name;
	}

	@Override
	public Optional<byte[]> read(String url) {
		String prefix = publicBaseUrl + "/";
		if (!url.startsWith(prefix)) {
			return Optional.empty();
		}
		Path path = root.resolve(url.substring(prefix.length())).normalize();
		if (!path.startsWith(root) || !Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(path));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
