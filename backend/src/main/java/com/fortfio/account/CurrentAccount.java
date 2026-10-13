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
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			return Long.valueOf(token.getToken().getSubject());
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
	}
}
