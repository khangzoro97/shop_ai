package com.fortfio.trial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.account.Role;
import com.fortfio.ai.GenerationSpec;
import com.fortfio.ai.Workflow;
import com.fortfio.common.ApiException;
import com.fortfio.config.AppProperties;
import com.fortfio.generation.GenerationJob;
import com.fortfio.generation.GenerationJobRepository;
import com.fortfio.generation.GenerationService;
import com.fortfio.generation.GenerationStatus;
import com.fortfio.media.FileStorage;
import com.fortfio.media.RemoteFiles;

import lombok.RequiredArgsConstructor;

/**
 * "Thử trước, đăng ký sau": a visitor uploads one product photo on the Home page and gets one
 * watermarked preview. The job belongs to a system account until the visitor signs up and claims
 * it, which moves it into their history with the original file.
 */
@Service
@RequiredArgsConstructor
public class TrialService {

	static final String SYSTEM_EMAIL = "trial@system.invalid";
	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final String NOT_FOUND = "Không tìm thấy ảnh thử, hãy tạo lại";

	/** Scenes offered on the Home page; the text goes to the AI as the customer's description. */
	public enum Scene {
		MARBLE("Đặt sản phẩm trên mặt bàn đá cẩm thạch trắng, nắng sớm chiếu xiên, bóng lá cây mờ phía sau"),
		PASTEL("Nền pastel hồng nhạt với vài khối hình học, ánh sáng studio mềm, phong cách tối giản"),
		NATURE("Sản phẩm đặt trên tảng đá phủ rêu giữa lá xanh và hoa nhỏ ngoài trời, nắng chiều ấm");

		private final String brief;

		Scene(String brief) {
			this.brief = brief;
		}
	}

	private final GenerationService generationService;
	private final GenerationJobRepository jobs;
	private final TrialRunRepository runs;
	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final FileStorage storage;
	private final RemoteFiles remoteFiles;
	private final AppProperties properties;
	private final SecureRandom random = new SecureRandom();

	public GenerationJob start(String ip, String image, Scene scene) {
		AppProperties.Trial config = properties.trial();
		if (!config.enabled()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Chưa mở dùng thử");
		}
		String ipHash = hash(ip);
		if (runs.countByIpHashAndCreatedAtAfter(ipHash, Instant.now().minus(Duration.ofDays(1))) >= config.perIpPerDay()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Bạn đã dùng hết lượt thử hôm nay. Đăng ký để nhận 30 credit miễn phí và tạo tiếp nhé!");
		}
		Instant today = LocalDate.now(VIETNAM).atStartOfDay(VIETNAM).toInstant();
		if (runs.countByCreatedAtAfter(today) >= config.maxPerDay()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Hôm nay đã hết lượt thử miễn phí. Đăng ký để nhận 30 credit và tạo ảnh ngay!");
		}
		GenerationSpec spec = new GenerationSpec(Workflow.PRODUCT_EXACT, null, null, null, null, null, 1, null,
				scene.brief, null);
		GenerationJob job = generationService.createFree(systemAccountId(), spec, Map.of("product", image));
		runs.save(new TrialRun(job.getId(), ipHash));
		return job;
	}

	/** Only unclaimed trials are visible here; a claimed one lives in its owner's Studio. */
	@Transactional(readOnly = true)
	public GenerationJob get(UUID id) {
		return jobs.findByIdAndAccountId(id, systemAccountId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
	}

	public byte[] preview(UUID id, int variant) {
		GenerationJob job = get(id);
		List<String> outputs = job.outputUrlList();
		if (job.getStatus() != GenerationStatus.SUCCEEDED || variant < 0 || variant >= outputs.size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Ảnh chưa sẵn sàng");
		}
		String url = outputs.get(variant);
		byte[] original = storage.read(url)
				.or(() -> remoteFiles.isAllowed(url) ? Optional.of(remoteFiles.download(url).data()) : Optional.empty())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ảnh không còn trên hệ thống"));
		return Watermark.preview(original, properties.trial().previewSize());
	}

	@Transactional
	public GenerationJob claim(UUID id, Long accountId) {
		GenerationJob job = get(id);
		if (job.getStatus() != GenerationStatus.SUCCEEDED) {
			throw new ApiException(HttpStatus.CONFLICT, job.getStatus() == GenerationStatus.RUNNING
					? "Ảnh thử vẫn đang được tạo, đợi chút rồi thử lại"
					: "Ảnh thử không tạo được, hãy tạo ảnh mới trong AI Studio");
		}
		job.setAccountId(accountId);
		return jobs.save(job);
	}

	private Long systemAccountId() {
		return accounts.findByEmail(SYSTEM_EMAIL).map(Account::getId).orElseGet(this::createSystemAccount);
	}

	private Long createSystemAccount() {
		byte[] secret = new byte[32];
		random.nextBytes(secret);
		Account account = new Account(SYSTEM_EMAIL, passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)),
				"Dùng thử", 0);
		account.setRole(Role.SYSTEM);
		try {
			return accounts.saveAndFlush(account).getId();
		} catch (DataIntegrityViolationException ex) {
			return accounts.findByEmail(SYSTEM_EMAIL).orElseThrow().getId();
		}
	}

	private static String hash(String ip) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(("trial:" + ip).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
