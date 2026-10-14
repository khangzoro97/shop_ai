package com.fortfio.auth;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.account.Account;
import com.fortfio.account.AccountController.AccountResponse;
import com.fortfio.account.CurrentAccount;
import com.fortfio.config.AppProperties;
import com.fortfio.security.RateLimiter;
import com.fortfio.security.TokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private static final String TOO_MANY_ATTEMPTS = "Bạn thao tác quá nhanh, hãy thử lại sau 1 phút";

	private final AuthService authService;
	private final PhoneVerificationService phoneVerification;
	private final PasswordResetService passwordReset;
	private final GoogleAuthService googleAuth;
	private final TokenService tokenService;
	private final CurrentAccount currentAccount;
	private final RateLimiter rateLimiter;
	private final AppProperties properties;

	@PostMapping("/api/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		limitByIp(http);
		return authenticated(authService.register(request.email(), request.password(), request.shopName()));
	}

	@PostMapping("/api/auth/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		limitByIp(http);
		return authenticated(authService.login(request.email(), request.password()));
	}

	@PostMapping("/api/auth/google")
	public AuthResponse google(@Valid @RequestBody GoogleRequest request, HttpServletRequest http) {
		limitByIp(http);
		return authenticated(googleAuth.login(request.credential()));
	}

	@GetMapping("/api/auth/options")
	public AuthOptions options() {
		return new AuthOptions(properties.auth().googleClientId());
	}

	@PostMapping("/api/auth/password/forgot")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
		passwordReset.request(request.email(), http.getRemoteAddr());
	}

	@PostMapping("/api/auth/password/reset")
	public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
		limitByIp(http);
		return authenticated(passwordReset.reset(request.token(), request.password()));
	}

	@PostMapping("/api/me/phone/otp")
	public PhoneVerificationService.SentCode sendOtp(@Valid @RequestBody PhoneRequest request) {
		return phoneVerification.send(currentAccount.id(), request.phone());
	}

	@PostMapping("/api/me/phone/verify")
	public AccountResponse verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
		return AccountResponse.from(phoneVerification.verify(currentAccount.id(), request.phone(), request.code()));
	}

	private void limitByIp(HttpServletRequest http) {
		rateLimiter.check("auth:" + http.getRemoteAddr(), properties.rateLimit().authPerMinute(),
				Duration.ofMinutes(1), TOO_MANY_ATTEMPTS);
	}

	private AuthResponse authenticated(Account account) {
		return new AuthResponse(tokenService.issue(account), AccountResponse.from(account));
	}

	public record RegisterRequest(
			@NotBlank(message = "Vui lòng nhập email")
			@Email(message = "Email không hợp lệ")
			@Size(max = 120, message = "Email quá dài") String email,
			@NotBlank(message = "Vui lòng nhập mật khẩu")
			@Size(min = 8, max = 72, message = "Mật khẩu cần từ 8 đến 72 ký tự") String password,
			@NotBlank(message = "Vui lòng nhập tên shop")
			@Size(max = 80, message = "Tên shop tối đa 80 ký tự") String shopName) {
	}

	public record LoginRequest(
			@NotBlank(message = "Vui lòng nhập email") String email,
			@NotBlank(message = "Vui lòng nhập mật khẩu") String password) {
	}

	public record GoogleRequest(@NotBlank @Size(max = 4096) String credential) {
	}

	/** @param googleClientId empty when Google login is off */
	public record AuthOptions(String googleClientId) {
	}

	public record ForgotPasswordRequest(
			@NotBlank(message = "Vui lòng nhập email")
			@Email(message = "Email không hợp lệ")
			@Size(max = 120, message = "Email quá dài") String email) {
	}

	public record ResetPasswordRequest(
			@NotBlank(message = "Đường dẫn không hợp lệ") @Size(max = 100, message = "Đường dẫn không hợp lệ") String token,
			@NotBlank(message = "Vui lòng nhập mật khẩu mới")
			@Size(min = 8, max = 72, message = "Mật khẩu cần từ 8 đến 72 ký tự") String password) {
	}

	public record PhoneRequest(@NotBlank(message = "Vui lòng nhập số điện thoại") String phone) {
	}

	public record VerifyPhoneRequest(
			@NotBlank(message = "Vui lòng nhập số điện thoại") String phone,
			@NotBlank(message = "Vui lòng nhập mã xác minh") @Size(max = 6, message = "Mã gồm 6 chữ số") String code) {
	}

	public record AuthResponse(String token, AccountResponse account) {
	}
}
