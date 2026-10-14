package com.fortfio.generation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fortfio.ai.Workflow;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

	Page<GenerationJob> findByAccountIdAndWorkflowInAndStatusInOrderByCreatedAtDesc(Long accountId,
			Collection<Workflow> workflows, Collection<GenerationStatus> statuses, Pageable pageable);

	Optional<GenerationJob> findByIdAndAccountId(UUID id, Long accountId);

	List<GenerationJob> findByStatus(GenerationStatus status);
}
