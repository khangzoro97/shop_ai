package com.fortfio.auth;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder until an SMS or Zalo ZNS sender replaces it: the code only appears in the server
 * log, so an admin can relay it to the shop manually (e.g. over Zalo).
 */
@Slf4j
@Component
public class LoggingOtpSender implements OtpSender {

	@Override
	public void send(String phone, String code) {
		log.info("OTP for {}: {}", phone, code);
	}
}
