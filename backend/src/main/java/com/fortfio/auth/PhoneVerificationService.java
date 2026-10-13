package com.fortfio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.security.RateLimiter;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Signup credits are granted only once a phone number is verified, and a phone number can be
 * verified on one account only, so creating many accounts does not yield more free credits.
 */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

	private final AccountRepository accounts;
	private final PhoneOtpRepository otps;
	private final OtpSender sender;
	private final RateLimiter rateLimiter;
	private final AppProperties properties;
	private final EntityManager entityManager;
	private final SecureRandom random = new SecureRandom();

	public record SentCode(String phone, long expiresInSeconds, String devCode) {
	}

	@Transactional
	public SentCode send(Long accountId, String rawPhone) {
		Account account = account(accountId);
		if (account.isPhoneVerified()) {
			throw new ApiException(HttpStatus.CONFLICT, "Tài khoản đã xác minh số điện thoại");
		}
		String phone = PhoneNumbers.normalize(rawPhone);
		if (accounts.existsByPhone(phone)) {
			throw new ApiException(HttpStatus.CONFLICT, "Số điện thoại này đã được dùng cho tài khoản khác");
		}

		AppProperties.Otp config = properties.auth().otp();
		Instant now = Instant.now();
		PhoneOtp entry = otps.findById(accountId).orElseGet(() -> new PhoneOtp(accountId));
		if (entry.getSentAt() != null && now.isBefore(entry.getSentAt().plus(config.resendCooldown()))) {
			long wait = Duration.between(now, entry.getSentAt().plus(config.resendCooldown())).toSeconds() + 1;
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi " + wait + " giây rồi gửi lại mã");
		}
		rateLimiter.check("otp:" + accountId, properties.rateLimit().otpPerHour(), Duration.ofHours(1),
				"Bạn đã yêu cầu quá nhiều mã, hãy thử lại sau 1 giờ");

		String code = "%06d".formatted(random.nextInt(1_000_000));
		entry.setPhone(phone);
		entry.setCodeHash(hash(accountId, code));
		entry.setSentAt(now);
		entry.setExpiresAt(now.plus(config.ttl()));
		entry.setAttempts(0);
		otps.save(entry);
		sender.send(phone, code);
		return new SentCode(phone, config.ttl().toSeconds(), config.exposeCode() ? code : null);
	}

	/** Failed attempts must be persisted even though an error is returned, hence no rollback. */
	@Transactional(noRollbackFor = ApiException.class)
	public Account verify(Long accountId, String rawPhone, String code) {
		String phone = PhoneNumbers.normalize(rawPhone);
		PhoneOtp entry = otps.findById(accountId)
				.filter(e -> e.getPhone().equals(phone))
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Hãy bấm gửi mã xác minh trước"));
		if (Instant.now().isAfter(entry.getExpiresAt())) {
			otps.delete(entry);
			throw new ApiException(HttpStatus.BAD_REQUEST, "Mã đã hết hạn, hãy yêu cầu mã mới");
		}
		int maxAttempts = properties.auth().otp().maxAttempts();
		if (!MessageDigest.isEqual(entry.getCodeHash().getBytes(StandardCharsets.UTF_8),
				hash(accountId, code.trim()).getBytes(StandardCharsets.UTF_8))) {
			entry.setAttempts(entry.getAttempts() + 1);
			if (entry.getAttempts() >= maxAttempts) {
				otps.delete(entry);
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Nhập sai quá nhiều lần, hãy yêu cầu mã mới");
			}
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Mã xác minh không đúng (còn " + (maxAttempts - entry.getAttempts()) + " lần thử)");
		}
		otps.delete(entry);

		Account account = account(accountId);
		if (account.isPhoneVerified()) {
			throw new ApiException(HttpStatus.CONFLICT, "Tài khoản đã xác minh số điện thoại");
		}
		if (accounts.existsByPhone(phone)) {
			throw new ApiException(HttpStatus.CONFLICT, "Số điện thoại này đã được dùng cho tài khoản khác");
		}
		account.setPhone(phone);
		account.setPhoneVerifiedAt(Instant.now());
		accounts.saveAndFlush(account);
		accounts.addCredits(accountId, properties.signupCredits());
		entityManager.refresh(account);
		return account;
	}

	private Account account(Long accountId) {
		return accounts.findById(accountId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
	}

	private static String hash(Long accountId, String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest((accountId + ":" + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
