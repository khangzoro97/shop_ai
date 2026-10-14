package com.fortfio.trial;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrialRunRepository extends JpaRepository<TrialRun, UUID> {

	long countByCreatedAtAfter(Instant since);

	long countByIpHashAndCreatedAtAfter(String ipHash, Instant since);
}
