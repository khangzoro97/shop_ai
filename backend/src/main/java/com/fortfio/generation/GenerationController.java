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
import com.fortfio.ai.OutputFormat;
import com.fortfio.ai.Placement;
import com.fortfio.ai.Workflow;
import com.fortfio.ai.WorkflowInputBuilder;
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
		GenerationSpec spec = new GenerationSpec(request.workflow(), request.style(), request.prompt(),
				request.format(), request.placement(), request.productName(),
				request.variants() == null ? 1 : request.variants());
		return GenerationResponse.from(generationService.create(accountId, spec, request.images()));
	}

	@PostMapping("/generations/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GenerationResponse retry(@PathVariable UUID id) {
		return GenerationResponse.from(generationService.retry(limitedAccountId(), id));
	}

	private Long limitedAccountId() {
		Long accountId = currentAccount.id();
		rateLimiter.check("generate:" + accountId, properties.rateLimit().generationsPerMinute(), Duration.ofMinutes(1),
				"Bạn đang tạo quá nhanh, hãy đợi 1 phút rồi thử lại");
		return accountId;
	}

	@GetMapping("/generations")
	public GenerationPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size) {
		Page<GenerationJob> result = generationService.page(currentAccount.id(), page, size);
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
			@NotEmpty Map<String, String> images) {
	}

	public record WorkflowResponse(
			Workflow id,
			String label,
			String description,
			int credits,
			int maxVariants,
			Workflow.OutputType outputType,
			boolean preservesProduct,
			List<Workflow.ImageSlot> imageSlots,
			List<StyleOption> styles,
			List<FormatOption> formats,
			List<PlacementOption> placements) {

		static WorkflowResponse from(Workflow w, int maxVariants) {
			return new WorkflowResponse(w, w.getLabel(), w.getDescription(), w.getCredits(), maxVariants,
					w.getOutputType(),
					w.preservesProduct(),
					w.getImageSlots(),
					w.getStyles().stream().map(s -> new StyleOption(s.id(), s.label())).toList(),
					w.formats().stream().map(f -> new FormatOption(f, f.getLabel(), f.getWidth(), f.getHeight())).toList(),
					w.placements().stream().map(p -> new PlacementOption(p, p.getLabel())).toList());
		}
	}

	public record StyleOption(String id, String label) {
	}

	public record PlacementOption(Placement id, String label) {
	}

	public record FormatOption(OutputFormat id, String label, int width, int height) {
	}

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
			boolean canRetry,
			Instant createdAt) {

		static GenerationResponse from(GenerationJob job) {
			return new GenerationResponse(job.getId(), job.getWorkflow(), job.getWorkflow().getOutputType(),
					job.getStyle(), job.getFormat(), job.getStatus(), job.getCreditsCharged(), job.outputUrlList(),
					job.getErrorMessage(), job.getQualityWarning(), job.getVariants(), job.canRetry(),
					job.getCreatedAt());
		}
	}
}
