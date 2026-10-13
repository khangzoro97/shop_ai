package com.fortfio.auth;

/** Delivers verification codes, e.g. by SMS (eSMS, SpeedSMS) or Zalo ZNS. */
public interface OtpSender {

	void send(String phone, String code);
}
