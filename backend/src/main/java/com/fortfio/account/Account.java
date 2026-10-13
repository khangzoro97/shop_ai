package com.fortfio.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	private String shopName;

	@Column(nullable = false)
	private int credits;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public Account(String email, String shopName, int credits) {
		this.email = email;
		this.shopName = shopName;
		this.credits = credits;
	}
}
