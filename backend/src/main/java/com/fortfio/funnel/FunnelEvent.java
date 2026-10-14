package com.fortfio.funnel;

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

/** A step only the browser sees (page visit, button click, download); the rest is read from accounts and jobs. */
@Entity
@Getter
@NoArgsConstructor
public class FunnelEvent {

	public enum Name {
		VISIT, CLICK_CREATE, TRIAL, CLICK_ZALO, DOWNLOAD
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Name name;

	@Column(nullable = false, length = 64)
	private String visitorId;

	private Long accountId;

	@Column(nullable = false)
	private Instant createdAt;

	public FunnelEvent(Name name, String visitorId, Long accountId) {
		this.name = name;
		this.visitorId = visitorId;
		this.accountId = accountId;
		this.createdAt = Instant.now();
	}
}
