package com.fortfio.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PasswordReset {

	@Id
	private Long accountId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	public PasswordReset(Long accountId) {
		this.accountId = accountId;
	}
}
