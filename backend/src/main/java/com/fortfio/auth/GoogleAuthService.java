package com.fortfio.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;

/**
 * "Đăng nhập bằng Google": the browser gets an ID token from Google Identity Services and we
 * check its signature, issuer and audience. Google has verified the email, so it logs into the
 * account with that email, or creates one (credits still come with phone verification).
 */
@Service
public class GoogleAuthService {

	private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
	private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");
	private static final int MAX_SHOP_NAME = 80;

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties properties;
	private final SecureRandom random = new SecureRandom();
	private volatile JwtDecoder decoder;

	public GoogleAuthService(AccountRepository accounts, PasswordEncoder passwordEncoder, AppProperties properties) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Transactional
	public Account login(String credential) {
		if (!properties.auth().googleEnabled()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Chưa bật đăng nhập bằng Google");
		}
		Jwt token;
		try {
			token = decoder().decode(credential);
		} catch (JwtException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Không xác thực được tài khoản Google, hãy thử lại");
		}
		String email = token.getClaimAsString("email");
		if (email == null || !Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản Google này chưa xác minh email");
		}
		String normalized = AuthService.normalizeEmail(email);
		return accounts.findByEmail(normalized).orElseGet(() -> accounts.save(
				new Account(normalized, randomPasswordHash(), shopName(token.getClaimAsString("name"), normalized), 0)));
	}

	private JwtDecoder decoder() {
		if (decoder == null) {
			NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URL).build();
			String clientId = properties.auth().googleClientId();
			OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
					JwtValidators.createDefault(),
					new JwtClaimValidator<String>("iss", ISSUERS::contains),
					new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId)));
			nimbus.setJwtValidator(validator);
			decoder = nimbus;
		}
		return decoder;
	}

	/** Google accounts log in without a password until the owner sets one via "Quên mật khẩu". */
	private String randomPasswordHash() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
	}

	private static String shopName(String name, String email) {
		String value = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim();
		return value.length() > MAX_SHOP_NAME ? value.substring(0, MAX_SHOP_NAME) : value;
	}
}
