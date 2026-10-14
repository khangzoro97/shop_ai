package com.fortfio.trial;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.account.CurrentAccount;
import com.fortfio.generation.GenerationController.GenerationResponse;
import com.fortfio.generation.GenerationJob;
import com.fortfio.generation.GenerationStatus;
import com.fortfio.security.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TrialController {

	private static final int STARTS_PER_MINUTE = 3;

	private final TrialService trialService;
	private final CurrentAccount currentAccount;
	private final RateLimiter rateLimiter;

	@PostMapping("/api/trial")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public TrialResponse start(@Valid @RequestBody TrialRequest request, HttpServletRequest http) {
		rateLimiter.check("trial:" + http.getRemoteAddr(), STARTS_PER_MINUTE, Duration.ofMinutes(1),
				"Bạn thao tác quá nhanh, hãy thử lại sau 1 phút");
		return TrialResponse.from(trialService.start(http.getRemoteAddr(), request.image(), request.scene()));
	}

	@GetMapping("/api/trial/{id}")
	public TrialResponse get(@PathVariable UUID id) {
		return TrialResponse.from(trialService.get(id));
	}

	@GetMapping("/api/trial/{id}/preview/{variant}")
	public ResponseEntity<byte[]> preview(@PathVariable UUID id, @PathVariable int variant) {
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
				.body(trialService.preview(id, variant));
	}

	/** After signing up: moves the trial into the caller's Studio history, original file included. */
	@PostMapping("/api/trial/{id}/claim")
	public GenerationResponse claim(@PathVariable UUID id) {
		return GenerationResponse.from(trialService.claim(id, currentAccount.id()));
	}

	public record TrialRequest(@NotBlank String image, @NotNull TrialService.Scene scene) {
	}

	/** @param previews watermarked images, available once the status is SUCCEEDED */
	public record TrialResponse(UUID id, GenerationStatus status, String error, List<String> previews) {

		static TrialResponse from(GenerationJob job) {
			List<String> previews = job.getStatus() == GenerationStatus.SUCCEEDED
					? IntStream.range(0, job.outputUrlList().size())
							.mapToObj(i -> "/api/trial/" + job.getId() + "/preview/" + i)
							.toList()
					: List.of();
			return new TrialResponse(job.getId(), job.getStatus(), job.getErrorMessage(), previews);
		}
	}
}
