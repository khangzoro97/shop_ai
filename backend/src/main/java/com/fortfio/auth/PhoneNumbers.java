package com.fortfio.auth;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.fortfio.common.ApiException;

/** Vietnamese mobile numbers, normalized to the 10-digit "0xxxxxxxxx" form. */
final class PhoneNumbers {

	private static final Pattern MOBILE = Pattern.compile("0[35789]\\d{8}");

	private PhoneNumbers() {
	}

	static String normalize(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[\\s.()-]", "");
		if (digits.startsWith("+84")) {
			digits = "0" + digits.substring(3);
		} else if (digits.startsWith("84") && digits.length() == 11) {
			digits = "0" + digits.substring(2);
		}
		if (!MOBILE.matcher(digits).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Số điện thoại di động không hợp lệ");
		}
		return digits;
	}
}
