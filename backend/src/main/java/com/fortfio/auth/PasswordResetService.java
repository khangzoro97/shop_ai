package com.fortfio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.account.Role;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.security.RateLimiter;

import lombok.RequiredArgsConstructor;

/**
 * The request answers the same whether or not the email has an account, so the form cannot be
 * used to find out who is a customer.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private static final String INVALID_LINK = "Đường dẫn đã hết hạn hoặc đã được dùng, hãy yêu cầu đường dẫn mới";

	private final AccountRepository accounts;
	private final PasswordResetRepository resets;
	private final PasswordEncoder passwordEncoder;
	private final ResetMailer mailer;
	private final RateLimiter rateLimiter;
	private final AppProperties properties;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public void request(String email, String ip) {
		AppProperties.Reset config = properties.auth().passwordReset();
		String normalized = AuthService.normalizeEmail(email);
		String tooMany = "Bạn đã yêu cầu quá nhiều lần, hãy thử lại sau 1 giờ";
		rateLimiter.check("reset-ip:" + ip, config.perHour() * 3, Duration.ofHours(1), tooMany);
		rateLimiter.check("reset:" + normalized, config.perHour(), Duration.ofHours(1), tooMany);

		Account account = accounts.findByEmail(normalized).orElse(null);
		if (account == null || account.getRole() == Role.SYSTEM) {
			return;
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		PasswordReset reset = resets.findById(account.getId()).orElseGet(() -> new PasswordReset(account.getId()));
		reset.setTokenHash(hash(token));
		reset.setExpiresAt(Instant.now().plus(config.ttl()));
		resets.save(reset);
		mailer.send(account.getEmail(), properties.appUrl() + "/dat-lai-mat-khau?token=" + token);
	}

	/** An expired link is removed even though an error is returned, hence no rollback. */
	@Transactional(noRollbackFor = ApiException.class)
	public Account reset(String token, String password) {
		PasswordReset reset = resets.findByTokenHash(hash(token.trim()))
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK));
		resets.delete(reset);
		if (Instant.now().isAfter(reset.getExpiresAt())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK);
		}
		Account account = accounts.findById(reset.getAccountId())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK));
		account.setPasswordHash(passwordEncoder.encode(password));
		return account;
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
