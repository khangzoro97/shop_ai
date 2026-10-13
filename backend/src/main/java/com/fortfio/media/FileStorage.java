package com.fortfio.media;

import java.util.Base64;
import java.util.Optional;

public interface FileStorage {

	/** Stores the file and returns a URL the browser can load. */
	String save(byte[] data, String extension);

	/** Reads back a file previously returned by {@link #save}, empty if the URL is not ours. */
	Optional<byte[]> read(String url);

	default String saveDataUrl(String dataUrl) {
		String mimeType = dataUrl.substring("data:".length(), dataUrl.indexOf(';'));
		String subtype = mimeType.substring(mimeType.indexOf('/') + 1);
		String extension = "jpeg".equals(subtype) ? "jpg" : subtype;
		return save(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)), extension);
	}
}
