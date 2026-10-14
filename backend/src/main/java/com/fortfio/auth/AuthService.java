package com.fortfio.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.account.Role;
import com.fortfio.common.ApiException;

import lombok.RequiredArgsConstructor;

/** New accounts start with 0 credits: signup credits are granted after phone verification. */
@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String INVALID_LOGIN = "Email hoặc mật khẩu không đúng";

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public Account register(String email, String password, String shopName) {
		String normalized = normalizeEmail(email);
		if (accounts.existsByEmail(normalized)) {
			throw new ApiException(HttpStatus.CONFLICT, "Email này đã được đăng ký");
		}
		return accounts.save(new Account(normalized, passwordEncoder.encode(password), shopName.trim(), 0));
	}

	@Transactional(readOnly = true)
	public Account login(String email, String password) {
		Account account = accounts.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN));
		if (account.getRole() == Role.SYSTEM || !passwordEncoder.matches(password, account.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
		}
		return account;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
