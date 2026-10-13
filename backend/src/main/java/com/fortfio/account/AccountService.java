package com.fortfio.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.common.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accounts;

	@Transactional(readOnly = true)
	public Account get(Long accountId) {
		return accounts.findById(accountId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
	}

	@Transactional
	public void charge(Long accountId, int amount) {
		if (accounts.tryCharge(accountId, amount) == 0) {
			int balance = get(accountId).getCredits();
			throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
					"Không đủ credit: cần " + amount + ", còn " + balance);
		}
	}

	@Transactional
	public void refund(Long accountId, int amount) {
		accounts.addCredits(accountId, amount);
	}
}
