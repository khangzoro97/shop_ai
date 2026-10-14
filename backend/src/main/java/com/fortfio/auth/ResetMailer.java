package com.fortfio.auth;

/** Delivers the "đặt lại mật khẩu" link to the account's email. */
public interface ResetMailer {

	void send(String email, String link);
}
