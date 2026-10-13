package com.fortfio.account;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.account.AccountController.AccountResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

	private final AccountRepository accounts;

	@GetMapping
	public List<AccountResponse> list() {
		return accounts.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
				.map(AccountResponse::from)
				.toList();
	}
}
