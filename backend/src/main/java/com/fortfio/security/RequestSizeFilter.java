package com.fortfio.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fortfio.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Rejects oversized JSON bodies (images are sent as data URIs) before they are read into memory. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestSizeFilter extends OncePerRequestFilter {

	private final AppProperties properties;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long max = properties.uploads().maxRequestSize().toBytes();
		if (request.getContentLengthLong() > max) {
			response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("{\"status\":413,\"detail\":\"Dữ liệu gửi lên quá lớn (tối đa "
					+ properties.uploads().maxRequestSize().toMegabytes() + "MB)\"}");
			return;
		}
		chain.doFilter(request, response);
	}
}
