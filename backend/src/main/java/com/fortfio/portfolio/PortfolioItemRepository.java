package com.fortfio.portfolio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

	List<PortfolioItem> findAllByOrderBySortOrderAsc();

	List<PortfolioItem> findByCategoryOrderBySortOrderAsc(PortfolioItem.Category category);
}
