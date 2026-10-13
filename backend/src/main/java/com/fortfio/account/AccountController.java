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
		Account account = accountService.get(currentAccount.id());
		return new AccountResponse(account.getId(), account.getEmail(), account.getShopName(), account.getCredits());
	}

	public record AccountResponse(Long id, String email, String shopName, int credits) {
	}
}
