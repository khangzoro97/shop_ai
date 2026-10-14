package com.fortfio.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.fortfio.common.ApiException;

/** Resolves the caller's account id from the login token (its subject). */
@Component
public class CurrentAccount {

	public Long id() {
		Long id = optionalId();
		if (id == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
		}
		return id;
	}

	/** For endpoints open to visitors: null when nobody is logged in. */
	public Long optionalId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth instanceof JwtAuthenticationToken token ? Long.valueOf(token.getToken().getSubject()) : null;
	}
}
