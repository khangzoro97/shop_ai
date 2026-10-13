package com.fortfio.portfolio;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fortfio.ai.Workflow;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

	private final PortfolioItemRepository items;

	@GetMapping
	public List<PortfolioResponse> list(@RequestParam(required = false) PortfolioItem.Category category) {
		List<PortfolioItem> result = category == null
				? items.findAllByOrderBySortOrderAsc()
				: items.findByCategoryOrderBySortOrderAsc(category);
		return result.stream().map(PortfolioResponse::from).toList();
	}

	public record PortfolioResponse(
			Long id,
			String title,
			String client,
			PortfolioItem.Category category,
			Workflow workflow,
			Workflow.OutputType outputType,
			String beforeUrl,
			String afterUrl,
			boolean sample) {

		static PortfolioResponse from(PortfolioItem item) {
			return new PortfolioResponse(item.getId(), item.getTitle(), item.getClient(), item.getCategory(),
					item.getWorkflow(), item.getWorkflow().getOutputType(), item.getBeforeUrl(), item.getAfterUrl(),
					item.isSample());
		}
	}
}
