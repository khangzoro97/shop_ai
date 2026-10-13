package com.fortfio.ai;

/**
 * Everything the customer chose for one generation, besides the uploaded images.
 *
 * @param productName optional short description of the product, used to steer background removal
 * @param variants    number of alternative results; credits are charged per variant
 */
public record GenerationSpec(
		Workflow workflow,
		String style,
		String prompt,
		OutputFormat format,
		Placement placement,
		String productName,
		int variants) {
}
