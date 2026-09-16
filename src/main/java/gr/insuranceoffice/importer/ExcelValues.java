package gr.insuranceoffice.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * Converts the text of one Excel cell into a typed value, applying the
 * transformations of SPEC §10. The parse methods take text that has been
 * through {@link #blankToNull} and throw {@link IllegalArgumentException}
 * with a message for the clerk when it cannot be converted. Nothing is
 * guessed: an unexpected value is an error, never a default.
 */
final class ExcelValues {

	// The old files use "-" for an empty cell.
	private static final String EMPTY = "-";

	// \h also covers the no-break spaces Excel puts in formatted numbers.
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\h]+");

	// Greek formatting: "." groups thousands and "," starts the decimals.
	private static final Pattern GREEK_NUMBER = Pattern.compile("(?:\\d+|\\d{1,3}(?:\\.\\d{3})+)(?:,\\d+)?");

	// "180.50" typed with a decimal point. At most two decimals, so that
	// "1.234" is always read as a Greek thousands group.
	private static final Pattern POINT_DECIMAL = Pattern.compile("\\d+\\.\\d{1,2}");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);

	private static final Map<String, Surcharge> SURCHARGES = Map.of(
			matchKey("ΝΑΙ (Ν.Ο.Δ.)"), new Surcharge(true, SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ),
			matchKey("ΝΑΙ (Ε.Η.)"), new Surcharge(true, SurchargeType.ΗΛΙΚΙΑΣ),
			matchKey("ΟΧΙ"), new Surcharge(false, null));

	record Surcharge(boolean applies, SurchargeType type) {
	}

	private ExcelValues() {
	}

	/** Trimmed text, or {@code null} for a blank cell or "-". */
	static String blankToNull(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.strip();
		return trimmed.isEmpty() || trimmed.equals(EMPTY) ? null : trimmed;
	}

	/** "25/02/2026", also without leading zeros. */
	static LocalDate parseDate(String text) {
		try {
			return LocalDate.parse(text, DATE);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("μη έγκυρη ημερομηνία «" + text + "», αναμένεται ηη/μμ/εεεε");
		}
	}

	/** "103", "103,5", "1.598" or "103.5". */
	static BigDecimal parseDecimal(String text) {
		BigDecimal value = decimalOrNull(text);
		if (value == null) {
			throw new IllegalArgumentException("μη έγκυρος αριθμός «" + text + "»");
		}
		return value;
	}

	static Integer parseInteger(String text) {
		try {
			return parseDecimal(text).intValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("αναμένεται ακέραιος αριθμός, όχι «" + text + "»");
		}
	}

	static Short parseShort(String text) {
		try {
			return parseDecimal(text).shortValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("αναμένεται μικρός ακέραιος αριθμός, όχι «" + text + "»");
		}
	}

	/** "180,00 €" becomes 180.00. */
	static BigDecimal parseMoney(String text) {
		BigDecimal value = decimalOrNull(WHITESPACE.matcher(text.replace("€", "")).replaceAll(""));
		if (value == null) {
			throw new IllegalArgumentException("μη έγκυρο ποσό «" + text + "»");
		}
		try {
			return value.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("ποσό με περισσότερα από δύο δεκαδικά «" + text + "»");
		}
	}

	/** "100%" becomes 100; the percent sign is optional. */
	static BigDecimal parsePercentage(String text) {
		String number = WHITESPACE.matcher(text).replaceAll("");
		if (number.endsWith("%")) {
			number = number.substring(0, number.length() - 1);
		}
		BigDecimal value = decimalOrNull(number);
		if (value == null) {
			throw new IllegalArgumentException("μη έγκυρο ποσοστό «" + text + "»");
		}
		return value;
	}

	/** «ΝΑΙ (Ν.Ο.Δ.)», «ΝΑΙ (Ε.Η.)» or «ΟΧΙ». */
	static Surcharge parseSurcharge(String text) {
		Surcharge surcharge = SURCHARGES.get(matchKey(text));
		if (surcharge == null) {
			throw new IllegalArgumentException(
					"άγνωστη τιμή «" + text + "», αναμένεται ΝΑΙ (Ν.Ο.Δ.), ΝΑΙ (Ε.Η.) ή ΟΧΙ");
		}
		return surcharge;
	}

	/** One of the Greek enum values of DATA_MODEL. */
	static <E extends Enum<E>> E parseEnum(String text, Class<E> type) {
		String key = matchKey(text);
		return Arrays.stream(type.getEnumConstants())
				.filter(constant -> matchKey(constant.name()).equals(key))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("άγνωστη τιμή «" + text + "», αναμένεται "
						+ Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "))));
	}

	/**
	 * Comparison key for headers and fixed values: accents, case and spaces
	 * are ignored, through the one shared normalization.
	 */
	static String matchKey(String text) {
		return WHITESPACE.matcher(TextNormalizationUtils.normalizeText(text)).replaceAll("");
	}

	private static BigDecimal decimalOrNull(String text) {
		if (GREEK_NUMBER.matcher(text).matches()) {
			return new BigDecimal(text.replace(".", "").replace(',', '.'));
		}
		if (POINT_DECIMAL.matcher(text).matches()) {
			return new BigDecimal(text);
		}
		return null;
	}

}
