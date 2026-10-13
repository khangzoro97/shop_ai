package com.fortfio.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fortfio.common.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the caller's account. Until authentication is added every request
 * acts as the seeded demo shop, so this must not be deployed publicly as-is.
 */
@Component
@RequiredArgsConstructor
public class CurrentAccount {

	public static final String DEMO_EMAIL = "demo@fortfio.vn";

	private final AccountRepository accounts;

	public Long id() {
		return accounts.findByEmail(DEMO_EMAIL)
				.map(Account::getId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập"));
	}
}
