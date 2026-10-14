package com.fortfio.generation;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.account.CurrentAccount;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.InputChecker;
import com.fortfio.ai.MusicMood;
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.PromptWriter;
import com.fortfio.ai.VideoOptions;
import com.fortfio.ai.VideoResolution;
import com.fortfio.ai.Workflow;
import com.fortfio.ai.WorkflowInputBuilder;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.security.RateLimiter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenerationController {

	private final GenerationService generationService;
	private final WorkflowInputBuilder inputBuilder;
	private final PromptWriter promptWriter;
	private final InputChecker inputChecker;
	private final CurrentAccount currentAccount;
	private final RateLimiter rateLimiter;
	private final AppProperties properties;

	@GetMapping("/workflows")
	public List<WorkflowResponse> workflows() {
		return Arrays.stream(Workflow.values())
				.map(w -> WorkflowResponse.from(w, inputBuilder.maxVariants(w)))
				.toList();
	}

	@PostMapping("/generations")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GenerationResponse create(@Valid @RequestBody CreateGenerationRequest request) {
		Long accountId = limitedAccountId();
		VideoOptions video = request.workflow().isVideo()
				? new VideoOptions(request.durationSeconds() == null ? 0 : request.durationSeconds(),
						request.resolution(), request.music())
				: null;
		GenerationSpec spec = new GenerationSpec(request.workflow(), request.style(), request.prompt(),
				request.format(), request.placement(), request.productName(),
				request.variants() == null ? 1 : request.variants(), video, request.brief(), null);
		return GenerationResponse.from(generationService.create(accountId, spec, request.images()));
	}

	/** The ✦ button: rewrites the customer's description in more detail. Free, but rate limited. */
	@PostMapping("/prompts/assist")
	public AssistResponse assist(@Valid @RequestBody AssistRequest request) {
		Long accountId = currentAccount.id();
		rateLimiter.check("assist:" + accountId, properties.rateLimit().promptAssistsPerMinute(), Duration.ofMinutes(1),
				"Bạn bấm AI viết hơi nhiều, hãy đợi 1 phút rồi thử lại");
		if (!PromptWriter.supports(request.workflow())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Tính năng này không cần mô tả");
		}
		boolean hasImage = request.image() != null && !request.image().isBlank();
		if (hasImage) {
			inputBuilder.validateImage("Ảnh sản phẩm", request.image());
		} else if (request.text() == null || request.text().isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Hãy nhập vài chữ hoặc tải ảnh sản phẩm lên để AI viết giúp");
		}
		int seconds = request.durationSeconds() == null ? VideoOptions.DEFAULT.seconds() : request.durationSeconds();
		List<Integer> clips = request.workflow().isVideo() ? inputBuilder.clipSeconds(seconds) : List.of();
		return new AssistResponse(promptWriter.assist(request.workflow(), request.text(),
				hasImage ? request.image() : null, clips));
	}

	public record AssistRequest(
			@NotNull Workflow workflow,
			@Size(max = PromptWriter.MAX_BRIEF_LENGTH, message = "Mô tả tối đa 3000 ký tự") String text,
			String image,
			@Min(VideoOptions.MIN_SECONDS) @Max(VideoOptions.MAX_SECONDS) Integer durationSeconds) {
	}

	public record AssistResponse(String text) {
	}

	/** Warns about unsuitable photos right after upload, before any credit is spent. Free, rate limited. */
	@PostMapping("/inputs/check")
	public InputCheckResponse checkInput(@Valid @RequestBody InputCheckRequest request) {
		Long accountId = currentAccount.id();
		rateLimiter.check("input-check:" + accountId, properties.rateLimit().inputChecksPerMinute(),
				Duration.ofMinutes(1), "Bạn tải ảnh hơi nhiều, hãy đợi 1 phút rồi thử lại");
		if (!InputChecker.checks(request.workflow(), request.slot())) {
			return new InputCheckResponse(List.of());
		}
		inputBuilder.validateImage("Ảnh", request.image());
		return new InputCheckResponse(inputChecker.check(request.workflow(), request.slot(), request.image()));
	}

	public record InputCheckRequest(@NotNull Workflow workflow, @NotNull @Size(max = 32) String slot,
			@NotNull String image) {
	}

	public record InputCheckResponse(List<InputChecker.Issue> issues) {
	}

	@PostMapping("/generations/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GenerationResponse retry(@PathVariable UUID id) {
		return GenerationResponse.from(generationService.retry(limitedAccountId(), id));
	}

	@PostMapping("/generations/{id}/enhance")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GenerationResponse enhance(@PathVariable UUID id, @Valid @RequestBody EnhanceRequest request) {
		return GenerationResponse.from(generationService.enhance(limitedAccountId(), id, request.workflow(),
				request.style(), request.variant() == null ? 0 : request.variant()));
	}

	public record EnhanceRequest(@NotNull Workflow workflow, @Size(max = 32) String style, @Min(0) Integer variant) {
	}

	private Long limitedAccountId() {
		Long accountId = currentAccount.id();
		rateLimiter.check("generate:" + accountId, properties.rateLimit().generationsPerMinute(), Duration.ofMinutes(1),
				"Bạn đang tạo quá nhanh, hãy đợi 1 phút rồi thử lại");
		return accountId;
	}

	@GetMapping("/generations")
	public GenerationPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size,
			@RequestParam(required = false) List<Workflow> workflow,
			@RequestParam(required = false) GenerationStatus status) {
		Page<GenerationJob> result = generationService.page(currentAccount.id(), workflow, status, page, size);
		return new GenerationPage(result.getContent().stream().map(GenerationResponse::from).toList(),
				result.getNumber(), result.getTotalPages(), result.getTotalElements());
	}

	public record GenerationPage(List<GenerationResponse> items, int page, int totalPages, long totalItems) {
	}

	@GetMapping("/generations/{id}")
	public GenerationResponse get(@PathVariable UUID id) {
		return GenerationResponse.from(generationService.get(currentAccount.id(), id));
	}

	public record CreateGenerationRequest(
			@NotNull Workflow workflow,
			@Size(max = 32) String style,
			@Size(max = 500) String prompt,
			OutputFormat format,
			Placement placement,
			@Size(max = 80) String productName,
			@Min(1) @Max(4) Integer variants,
			@Min(VideoOptions.MIN_SECONDS) @Max(VideoOptions.MAX_SECONDS) Integer durationSeconds,
			VideoResolution resolution,
			MusicMood music,
			@Size(max = PromptWriter.MAX_BRIEF_LENGTH, message = "Mô tả tối đa 3000 ký tự") String brief,
			@NotEmpty Map<String, String> images) {
	}

	/** Choices and prices for video workflows; credits = seconds x creditsPerSecond (+ music). */
	public record VideoSettings(
			List<Integer> presetSeconds,
			int minSeconds,
			int maxSeconds,
			int defaultSeconds,
			List<ResolutionOption> resolutions,
			VideoResolution defaultResolution,
			List<MusicOption> music,
			int musicCredits) {

		static final VideoSettings INSTANCE = new VideoSettings(
				VideoOptions.PRESET_SECONDS,
				VideoOptions.MIN_SECONDS,
				VideoOptions.MAX_SECONDS,
				VideoOptions.DEFAULT.seconds(),
				Arrays.stream(VideoResolution.values())
						.map(r -> new ResolutionOption(r, r.getLabel(), r.getHint(), r.getCreditsPerSecond()))
						.toList(),
				VideoOptions.DEFAULT.resolution(),
				Arrays.stream(MusicMood.values()).map(m -> new MusicOption(m, m.getLabel(), m.getHint())).toList(),
				VideoOptions.MUSIC_CREDITS);
	}

	public record ResolutionOption(VideoResolution id, String label, String hint, int creditsPerSecond) {
	}

	public record MusicOption(MusicMood id, String label, String hint) {
	}

	public record WorkflowResponse(
			Workflow id,
			String label,
			String description,
			int credits,
			int maxVariants,
			Workflow.OutputType outputType,
			boolean preservesProduct,
			boolean enhancesResult,
			List<Workflow.ImageSlot> imageSlots,
			List<StyleOption> styles,
			List<FormatOption> formats,
			List<PlacementOption> placements,
			VideoSettings video) {

		static WorkflowResponse from(Workflow w, int maxVariants) {
			return new WorkflowResponse(w, w.getLabel(), w.getDescription(), w.getCredits(), maxVariants,
					w.getOutputType(),
					w.preservesProduct(),
					w.enhancesResult(),
					w.getImageSlots(),
					w.getStyles().stream().map(s -> new StyleOption(s.id(), s.group(), s.label(), s.hint())).toList(),
					w.formats().stream().map(f -> new FormatOption(f, f.getLabel(), f.getWidth(), f.getHeight())).toList(),
					w.placements().stream().map(p -> new PlacementOption(p, p.getLabel())).toList(),
					w.isVideo() ? VideoSettings.INSTANCE : null);
		}
	}

	public record StyleOption(String id, String group, String label, String hint) {
	}

	public record PlacementOption(Placement id, String label) {
	}

	public record FormatOption(OutputFormat id, String label, int width, int height) {
	}

	/**
	 * @param summary  simple mode: what the AI understood from the description, in Vietnamese
	 * @param planning simple mode: the AI is still reading the description
	 */
	public record GenerationResponse(
			UUID id,
			Workflow workflow,
			Workflow.OutputType outputType,
			String style,
			OutputFormat format,
			GenerationStatus status,
			int creditsCharged,
			List<String> outputUrls,
			String errorMessage,
			String qualityWarning,
			int variants,
			VideoOptions video,
			String brief,
			String summary,
			boolean planning,
			boolean canRetry,
			Instant createdAt) {

		public static GenerationResponse from(GenerationJob job) {
			boolean planning = job.getStatus() == GenerationStatus.RUNNING && job.getBrief() != null
					&& job.getPromptPlan() == null;
			return new GenerationResponse(job.getId(), job.getWorkflow(), job.getWorkflow().getOutputType(),
					job.getStyle(), job.getFormat(), job.getStatus(), job.getCreditsCharged(), job.outputUrlList(),
					job.getErrorMessage(), job.getQualityWarning(), job.getVariants(), job.video(), job.getBrief(),
					job.planSummary(), planning, job.canRetry(), job.getCreatedAt());
		}
	}
}
