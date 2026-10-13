package com.fortfio.account;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;
	private final CurrentAccount currentAccount;

	@GetMapping
	public AccountResponse me() {
		return AccountResponse.from(accountService.get(currentAccount.id()));
	}

	public record AccountResponse(Long id, String email, String shopName, Role role, int credits,
			String phone, boolean phoneVerified) {

		public static AccountResponse from(Account account) {
			return new AccountResponse(account.getId(), account.getEmail(), account.getShopName(), account.getRole(),
					account.getCredits(), account.getPhone(), account.isPhoneVerified());
		}
	}
}
