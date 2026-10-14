package com.fortfio.generation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.MusicMood;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.PromptPlan;
import com.fortfio.ai.StepKind;
import com.fortfio.ai.VideoOptions;
import com.fortfio.ai.VideoResolution;
import com.fortfio.ai.Workflow;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(indexes = {
		@Index(name = "idx_generation_account", columnList = "accountId, createdAt"),
		@Index(name = "idx_generation_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class GenerationJob {

	private static final String URL_SEPARATOR = "\n";

	@Id
	private UUID id = UUID.randomUUID();

	/** Optimistic lock: a stale copy cannot overwrite a newer state (and refund a second time). */
	@Version
	private Long version;

	@Column(nullable = false)
	private Long accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Workflow workflow;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private OutputFormat format;

	@Column(length = 32)
	private String style;

	@Column(length = 1000)
	private String prompt;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private Placement placement;

	@Column(length = 100)
	private String productName;

	@Column(nullable = false)
	private int variants = 1;

	private Integer videoSeconds;

	@Enumerated(EnumType.STRING)
	@Column(length = 8)
	private VideoResolution videoResolution;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private MusicMood music;

	/** Simple mode: the customer's description as submitted; null for the advanced form. */
	@Column(length = 3000)
	private String brief;

	/** {@link PromptPlan} as JSON, once the language model has answered. */
	@Column(length = 8000)
	private String promptPlan;

	@Column(length = 500)
	private String qualityWarning;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GenerationStatus status = GenerationStatus.RUNNING;

	@Column(nullable = false)
	private int creditsCharged;

	@OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@OrderBy("id")
	private List<GenerationStep> steps = new ArrayList<>();

	@Column(length = 4000)
	private String outputUrls;

	@Column(length = 2000)
	private String inputImages;

	@Column(length = 1000)
	private String errorMessage;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public GenerationStep addStep(StepKind kind, String model) {
		GenerationStep step = new GenerationStep(this, kind, model);
		steps.add(step);
		return step;
	}

	public GenerationSpec spec() {
		return new GenerationSpec(workflow, style, prompt, format, placement, productName, variants, video(), brief,
				plan());
	}

	public PromptPlan plan() {
		return PromptPlan.fromJson(promptPlan);
	}

	public void setPlan(PromptPlan plan) {
		promptPlan = plan == null ? null : plan.toJson();
	}

	/** What the AI understood from the description, in Vietnamese. */
	public String planSummary() {
		PromptPlan plan = plan();
		return plan == null ? null : plan.summary();
	}

	/** Null for images; video jobs created before these options existed were 5 seconds at 720p. */
	public VideoOptions video() {
		if (!workflow.isVideo()) {
			return null;
		}
		return videoSeconds == null ? VideoOptions.DEFAULT : new VideoOptions(videoSeconds, videoResolution, music);
	}

	public void apply(GenerationSpec spec) {
		workflow = spec.workflow();
		style = spec.style();
		prompt = spec.prompt();
		format = spec.format();
		placement = spec.placement();
		productName = spec.productName();
		variants = spec.variants();
		VideoOptions video = spec.video();
		videoSeconds = video == null ? null : video.seconds();
		videoResolution = video == null ? null : video.resolution();
		music = video == null ? null : video.music();
		brief = spec.brief();
		setPlan(spec.plan());
	}

	public String stepOutput(StepKind kind) {
		return stepOutputs(kind).getFirst();
	}

	public List<String> stepOutputs(StepKind kind) {
		return steps.stream()
				.filter(s -> s.getKind() == kind)
				.findFirst()
				.map(GenerationStep::outputUrls)
				.orElseThrow(() -> new IllegalStateException("Job " + id + " has no " + kind + " step"));
	}

	public boolean hasStep(StepKind kind) {
		return steps.stream().anyMatch(s -> s.getKind() == kind);
	}

	public boolean allStepsCompleted() {
		return !steps.isEmpty() && steps.stream().allMatch(GenerationStep::isCompleted);
	}

	public List<String> outputUrlList() {
		return outputUrls == null || outputUrls.isBlank() ? List.of() : Arrays.asList(outputUrls.split(URL_SEPARATOR));
	}

	/** Uploaded images by slot key, as stored copies (empty for jobs created before inputs were kept). */
	public Map<String, String> inputs() {
		Map<String, String> inputs = new LinkedHashMap<>();
		if (inputImages != null) {
			for (String line : inputImages.split(URL_SEPARATOR)) {
				int separator = line.indexOf('=');
				if (separator > 0) {
					inputs.put(line.substring(0, separator), line.substring(separator + 1));
				}
			}
		}
		return inputs;
	}

	public void setInputs(Map<String, String> inputs) {
		inputImages = inputs.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining(URL_SEPARATOR));
	}

	public boolean canRetry() {
		return status != GenerationStatus.RUNNING && !inputs().isEmpty();
	}

	public void succeed(List<String> urls) {
		status = GenerationStatus.SUCCEEDED;
		outputUrls = String.join(URL_SEPARATOR, urls);
	}

	public void fail(String message) {
		status = GenerationStatus.FAILED;
		errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
	}
}
