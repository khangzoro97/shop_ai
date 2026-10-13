package com.fortfio.portfolio;

import com.fortfio.ai.Workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String client;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Workflow workflow;

	@Column(length = 1000)
	private String beforeUrl;

	@Column(nullable = false, length = 1000)
	private String afterUrl;

	private int sortOrder;

	/** True for illustrative stock images; only real client work may be shown as case studies. */
	@Column(nullable = false)
	private boolean sample;

	public enum Category {
		FASHION, COSMETICS
	}
}
