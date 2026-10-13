package com.fortfio.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The pending verification code of an account; at most one per account. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PhoneOtp {

	@Id
	private Long accountId;

	@Column(nullable = false, length = 16)
	private String phone;

	@Column(nullable = false, length = 64)
	private String codeHash;

	@Column(nullable = false)
	private Instant sentAt;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	PhoneOtp(Long accountId) {
		this.accountId = accountId;
	}
}
