package com.fortfio.trial;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class TrialRun {

	@Id
	private UUID jobId;

	@Column(nullable = false, length = 64)
	private String ipHash;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public TrialRun(UUID jobId, String ipHash) {
		this.jobId = jobId;
		this.ipHash = ipHash;
	}
}
