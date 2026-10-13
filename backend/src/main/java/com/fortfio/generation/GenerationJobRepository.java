package com.fortfio.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

	List<GenerationJob> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

	Optional<GenerationJob> findByIdAndAccountId(UUID id, Long accountId);

	List<GenerationJob> findByStatus(GenerationStatus status);
}
