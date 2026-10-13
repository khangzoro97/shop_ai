package com.fortfio.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String shopName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Role role = Role.CUSTOMER;

	/** Set only once verified; unique so one phone number can claim signup credits only once. */
	@Column(unique = true, length = 16)
	private String phone;

	private Instant phoneVerifiedAt;

	@Column(nullable = false)
	private int credits;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public Account(String email, String passwordHash, String shopName, int credits) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.shopName = shopName;
		this.credits = credits;
	}

	public boolean isPhoneVerified() {
		return phoneVerifiedAt != null;
	}
}
