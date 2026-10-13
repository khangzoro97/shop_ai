package com.fortfio.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.account.Role;
import com.fortfio.ai.Workflow;
import com.fortfio.auth.AuthService;
import com.fortfio.portfolio.PortfolioItem;
import com.fortfio.portfolio.PortfolioItem.Category;
import com.fortfio.portfolio.PortfolioItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Portfolio seeds are stock photos (Unsplash) marked as samples: they must never carry
 * client names or a before/after pair, since they are not work done for a real shop.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

	private static final String UNSPLASH = "https://images.unsplash.com/";
	private static final String SAMPLE_CLIENT = "Ảnh minh họa";

	private final AccountRepository accounts;
	private final PortfolioItemRepository portfolio;
	private final AppProperties properties;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedAdmin();
		if (portfolio.count() == 0) {
			portfolio.saveAll(List.of(
					sample("Concept serum - studio", Category.COSMETICS, Workflow.PRODUCT_EXACT,
							"photo-1608248543803-ba4f8c70ae0b", 1),
					sample("Concept skincare - lifestyle", Category.COSMETICS, Workflow.PRODUCT_EXACT,
							"photo-1571781926291-c477ebfd024b", 2),
					sample("Concept son & phấn - sang trọng", Category.COSMETICS, Workflow.PRODUCT_PHOTO,
							"photo-1522335789203-aabd1fc54bc9", 3),
					sample("Concept váy hè - người mẫu", Category.FASHION, Workflow.VIRTUAL_TRYON,
							"photo-1515886657613-9f3515b0c78f", 4),
					sample("Concept lookbook thu đông", Category.FASHION, Workflow.VIRTUAL_TRYON,
							"photo-1539109136881-3be0616acf4b", 5),
					sample("Concept phụ kiện thời trang", Category.FASHION, Workflow.PRODUCT_PHOTO,
							"photo-1598440947619-2c35fc9aa908", 6)));
		}
	}

	/** The password is only used when the account is created; later changes go through the app. */
	private void seedAdmin() {
		AppProperties.Admin admin = properties.admin();
		if (!admin.configured()) {
			return;
		}
		String email = AuthService.normalizeEmail(admin.email());
		Account account = accounts.findByEmail(email)
				.orElseGet(() -> new Account(email, passwordEncoder.encode(admin.password()), "Fortfio Admin", 0));
		account.setRole(Role.ADMIN);
		accounts.save(account);
	}

	private static PortfolioItem sample(String title, Category category, Workflow workflow, String imageId, int order) {
		return new PortfolioItem(null, title, SAMPLE_CLIENT, category, workflow, null,
				UNSPLASH + imageId + "?w=800", order, true);
	}
}
