package com.fortfio.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.fortfio.account.Account;
import com.fortfio.config.AppProperties;

import lombok.RequiredArgsConstructor;

/** The role is baked into the token, so a role change applies at the user's next login. */
@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtEncoder encoder;
	private final AppProperties properties;

	public String issue(Account account) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("fortfio")
				.subject(account.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.auth().tokenTtl()))
				.claim(SecurityConfig.ROLE_CLAIM, account.getRole().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
