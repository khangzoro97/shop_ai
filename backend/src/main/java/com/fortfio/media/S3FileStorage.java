package com.fortfio.media;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fortfio.config.AppProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Stores files in Cloudflare R2 or Amazon S3. The bucket must be publicly readable through
 * {@code fortfio.storage.public-base-url} (R2: public bucket or custom domain).
 */
@Component
@ConditionalOnProperty(name = "fortfio.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage, DisposableBean {

	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"jpg", "image/jpeg",
			"png", "image/png",
			"webp", "image/webp",
			"mp4", "video/mp4",
			"webm", "video/webm");

	private final S3Client client;
	private final String bucket;
	private final String publicBaseUrl;

	public S3FileStorage(AppProperties properties) {
		AppProperties.S3 s3 = properties.storage().s3();
		if (isBlank(s3.bucket()) || isBlank(s3.accessKey()) || isBlank(s3.secretKey())) {
			throw new IllegalStateException("STORAGE_TYPE=s3 requires S3_BUCKET, S3_ACCESS_KEY and S3_SECRET_KEY");
		}
		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(s3.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));
		if (!isBlank(s3.endpoint())) {
			builder.endpointOverride(URI.create(s3.endpoint())).forcePathStyle(true);
		}
		this.client = builder.build();
		this.bucket = s3.bucket();
		this.publicBaseUrl = trimTrailingSlash(properties.storage().publicBaseUrl());
	}

	@Override
	public String save(byte[] data, String extension) {
		String key = LocalDate.now() + "/" + UUID.randomUUID() + "." + extension;
		client.putObject(request -> request
				.bucket(bucket)
				.key(key)
				.contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"))
				.cacheControl("public, max-age=31536000, immutable"),
				RequestBody.fromBytes(data));
		return publicBaseUrl + "/" + key;
	}

	@Override
	public Optional<byte[]> read(String url) {
		String prefix = publicBaseUrl + "/";
		if (!url.startsWith(prefix)) {
			return Optional.empty();
		}
		String key = url.substring(prefix.length());
		if (key.contains("..")) {
			return Optional.empty();
		}
		try {
			return Optional.of(client.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray());
		} catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
	}

	@Override
	public void destroy() {
		client.close();
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
