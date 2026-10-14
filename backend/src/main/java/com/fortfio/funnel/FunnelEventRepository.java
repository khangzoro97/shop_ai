package com.fortfio.funnel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FunnelEventRepository extends JpaRepository<FunnelEvent, Long> {
}
