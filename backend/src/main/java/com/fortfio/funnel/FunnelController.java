package com.fortfio.funnel;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.account.CurrentAccount;
import com.fortfio.security.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * Conversion funnel. Visits and clicks are counted per anonymous visitor id kept in the browser;
 * the later steps follow the accounts that signed up in the period.
 */
@RestController
@RequiredArgsConstructor
public class FunnelController {

	private static final int EVENTS_PER_MINUTE = 60;

	private final FunnelEventRepository events;
	private final CurrentAccount currentAccount;
	private final RateLimiter rateLimiter;
	private final JdbcTemplate jdbc;

	@PostMapping("/api/events")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void record(@Valid @RequestBody EventRequest request, HttpServletRequest http) {
		rateLimiter.check("events:" + http.getRemoteAddr(), EVENTS_PER_MINUTE, Duration.ofMinutes(1),
				"Quá nhiều sự kiện");
		events.save(new FunnelEvent(request.name(), request.visitorId(), currentAccount.optionalId()));
	}

	public record EventRequest(@NotNull FunnelEvent.Name name,
			@NotNull @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String visitorId) {
	}

	@GetMapping("/api/admin/funnel")
	public Funnel funnel(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
		Timestamp since = Timestamp.from(Instant.now().minus(Duration.ofDays(days)));
		List<Step> steps = new ArrayList<>();
		steps.add(new Step("Vào trang", visitors(since, FunnelEvent.Name.VISIT)));
		steps.add(new Step("Bấm \"Tạo ảnh\" hoặc thử ảnh",
				visitors(since, FunnelEvent.Name.CLICK_CREATE, FunnelEvent.Name.TRIAL)));
		steps.add(new Step("Đăng ký", count("""
				SELECT count(*) FROM account WHERE role = 'CUSTOMER' AND created_at >= ?""", since)));
		steps.add(new Step("Xác minh SĐT", count("""
				SELECT count(*) FROM account WHERE role = 'CUSTOMER' AND created_at >= ?
				AND phone_verified_at IS NOT NULL""", since)));
		steps.add(new Step("Tạo ảnh đầu tiên", count("""
				SELECT count(*) FROM account a WHERE a.role = 'CUSTOMER' AND a.created_at >= ?
				AND EXISTS (SELECT 1 FROM generation_job j WHERE j.account_id = a.id)""", since)));
		steps.add(new Step("Tải ảnh", count("""
				SELECT count(*) FROM account a WHERE a.role = 'CUSTOMER' AND a.created_at >= ?
				AND EXISTS (SELECT 1 FROM funnel_event e WHERE e.account_id = a.id AND e.name = 'DOWNLOAD')""",
				since)));
		return new Funnel(days, steps, visitors(since, FunnelEvent.Name.TRIAL),
				visitors(since, FunnelEvent.Name.CLICK_ZALO));
	}

	private long visitors(Timestamp since, FunnelEvent.Name... names) {
		String in = Arrays.stream(names).map(n -> "'" + n.name() + "'").collect(Collectors.joining(","));
		return count("SELECT count(DISTINCT visitor_id) FROM funnel_event WHERE name IN (" + in
				+ ") AND created_at >= ?", since);
	}

	private long count(String sql, Timestamp since) {
		Long value = jdbc.queryForObject(sql, Long.class, since);
		return value == null ? 0 : value;
	}

	/** Purchases join the funnel once online payment exists (checklist section 5). */
	/** @param trials visitors who tried an image on the Home page without an account */
	public record Funnel(int days, List<Step> steps, long trials, long zaloClicks) {
	}

	public record Step(String label, long count) {
	}
}
