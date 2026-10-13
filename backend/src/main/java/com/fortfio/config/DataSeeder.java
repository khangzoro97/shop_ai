package com.fortfio.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortfio.account.Account;
import com.fortfio.account.AccountRepository;
import com.fortfio.account.CurrentAccount;
import com.fortfio.ai.Workflow;
import com.fortfio.portfolio.PortfolioItem;
import com.fortfio.portfolio.PortfolioItem.Category;
import com.fortfio.portfolio.PortfolioItemRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

	private static final String UNSPLASH = "https://images.unsplash.com/";

	private final AccountRepository accounts;
	private final PortfolioItemRepository portfolio;
	private final AppProperties properties;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (accounts.findByEmail(CurrentAccount.DEMO_EMAIL).isEmpty()) {
			accounts.save(new Account(CurrentAccount.DEMO_EMAIL, "Shop Demo", properties.signupCredits()));
		}
		if (portfolio.count() == 0) {
			portfolio.saveAll(List.of(
					item("Serum dưỡng ẩm - ảnh studio", "Glow Skin", Category.COSMETICS, Workflow.PRODUCT_PHOTO,
							"photo-1620916566398-39f1143ab7be", "photo-1608248543803-ba4f8c70ae0b", 1),
					item("Bộ skincare - lifestyle", "Mộc Beauty", Category.COSMETICS, Workflow.PRODUCT_PHOTO,
							"photo-1556228578-8c89e6adf883", "photo-1571781926291-c477ebfd024b", 2),
					item("Son & phấn - concept sang trọng", "Rosé Cosmetics", Category.COSMETICS, Workflow.PRODUCT_PHOTO,
							null, "photo-1522335789203-aabd1fc54bc9", 3),
					item("Váy hè - thử đồ ảo", "Linh Boutique", Category.FASHION, Workflow.VIRTUAL_TRYON,
							"photo-1490481651871-ab68de25d43d", "photo-1515886657613-9f3515b0c78f", 4),
					item("Lookbook thu đông", "Saigon Street", Category.FASHION, Workflow.VIRTUAL_TRYON,
							"photo-1483985988355-763728e1935b", "photo-1539109136881-3be0616acf4b", 5),
					item("Phụ kiện thời trang", "Mây Studio", Category.FASHION, Workflow.PRODUCT_PHOTO,
							null, "photo-1598440947619-2c35fc9aa908", 6)));
		}
	}

	private static PortfolioItem item(String title, String client, Category category, Workflow workflow,
			String beforeId, String afterId, int order) {
		return new PortfolioItem(null, title, client, category, workflow,
				beforeId == null ? null : UNSPLASH + beforeId + "?w=800",
				UNSPLASH + afterId + "?w=800", order);
	}
}
