package com.fortfio.generation;

import java.util.List;

import com.fortfio.ai.AiProvider;
import com.fortfio.ai.StepKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class GenerationStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id")
	private GenerationJob job;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private StepKind kind;

	@Column(nullable = false)
	private String model;

	private String providerRequestId;

	@Column(length = 1000)
	private String providerStatusUrl;

	@Column(length = 1000)
	private String providerResponseUrl;

	/** Media output; MAIN steps hold one URL per variant, separated by newlines. */
	@Column(length = 2000)
	private String outputUrl;

	/** Text output of OCR steps. */
	@Column(length = 4000)
	private String outputText;

	GenerationStep(GenerationJob job, StepKind kind, String model) {
		this.job = job;
		this.kind = kind;
		this.model = model;
	}

	public boolean isCompleted() {
		return outputUrl != null || outputText != null;
	}

	public List<String> outputUrls() {
		return outputUrl == null || outputUrl.isBlank() ? List.of() : List.of(outputUrl.split("\n"));
	}

	void completeText(String text) {
		String value = text == null ? "" : text;
		outputText = value.substring(0, Math.min(value.length(), 4000));
	}

	public AiProvider.Submission submission() {
		return new AiProvider.Submission(providerRequestId, providerStatusUrl, providerResponseUrl);
	}

	void attach(AiProvider.Submission submission) {
		providerRequestId = submission.requestId();
		providerStatusUrl = submission.statusUrl();
		providerResponseUrl = submission.responseUrl();
	}

	void complete(List<String> urls) {
		outputUrl = String.join("\n", urls);
	}
}
