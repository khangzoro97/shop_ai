package com.fortfio.ai;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares OCR text of the uploaded product with OCR text of an AI result, to flag labels the
 * AI rewrote. Only distinctive tokens count (numbers such as volumes or SPF, words of 4+
 * letters), so small OCR misreads of short words do not raise false alarms.
 */
public final class LabelTextCheck {

	private static final Pattern TOKEN = Pattern.compile("[A-Z0-9]+(?:[.,][0-9]+)?");
	private static final int MIN_WORD_LENGTH = 4;

	private LabelTextCheck() {
	}

	/** Distinctive label tokens of the source that do not appear in the result, in reading order. */
	public static Set<String> missingTokens(String sourceText, String resultText) {
		Set<String> missing = tokens(sourceText);
		missing.removeAll(tokens(resultText));
		return missing;
	}

	public static Set<String> tokens(String text) {
		Set<String> tokens = new LinkedHashSet<>();
		if (text == null) {
			return tokens;
		}
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toUpperCase(Locale.ROOT);
		Matcher matcher = TOKEN.matcher(normalized);
		while (matcher.find()) {
			String token = matcher.group().replace(',', '.');
			boolean hasDigit = token.chars().anyMatch(Character::isDigit);
			if (hasDigit || token.length() >= MIN_WORD_LENGTH) {
				tokens.add(token);
			}
		}
		return tokens;
	}
}
