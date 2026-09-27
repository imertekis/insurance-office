package gr.insuranceoffice.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * The fixed sets of values of a vehicle (Task 23a). Category (J), Euro (V.9)
 * and colour (R) are here, in code: regulations and the licence define them,
 * and they rarely change. The brands are in the {@code vehicle_brand} table,
 * read into {@link Brands}.
 * <p>
 * Two uses, one place. The form's rule ({@link VehicleService}): a new or
 * changed value must be one of the list, written as the list writes it. The
 * Excel import's mapping ({@link #category}, {@link #emissionStandard},
 * {@link #color}, {@link Brands#match}): case and accents aside, and through
 * the synonyms, a value becomes the list's own spelling; a value that could
 * mean more than one thing matches nothing and is kept as it came, for the
 * clerk to correct (decision 6). V8 applied the same mapping, in SQL, to the
 * vehicles already stored.
 */
public final class VehicleValues {

	/** The EU's basic categories, as the licence writes them in J (decision 1). */
	public static final List<String> CATEGORIES = List.of("M1", "M2", "M3", "N1", "N2", "N3", "O1", "O2", "O3", "O4",
			"L1e", "L2e", "L3e", "L4e", "L5e", "L6e", "L7e", "T");

	/** Decision 2. Empty is allowed too: the column is nullable. */
	public static final List<String> EMISSION_STANDARDS = List.of("Euro 1", "Euro 2", "Euro 3", "Euro 4", "Euro 5",
			"Euro 6", "ZEV");

	/**
	 * The licence's colours (decision 3). A vehicle of two colours stores both
	 * in the one column, "Λευκό-Μαύρο"; of more than two, {@link #MULTICOLOURED}.
	 */
	public static final List<String> COLORS = List.of("Λευκό", "Μαύρο", "Γκρι", "Ασημί", "Μπλε", "Κόκκινο",
			"Πράσινο", "Κίτρινο", "Πορτοκαλί", "Καφέ", "Μπεζ", "Μπορντό", "Μωβ", "Ροζ", "Χρυσαφί");

	public static final String MULTICOLOURED = "Πολύχρωμο";

	private static final String SECOND_COLOR = "-";

	// Other ways the files write a colour, each with a single meaning; case
	// and accents need none. «ΔΙΧΡΩΜΟ» says nothing about which two.
	private static final Map<String, String> COLOR_SYNONYMS = Map.of(
			"ΑΣΠΡΟ", "Λευκό",
			"ΑΣΗΜΕΝΙΟ", "Ασημί",
			"ΓΚΡΙΖΟ", "Γκρι",
			"ΚΑΦΕΤΙ", "Καφέ",
			"ΧΡΥΣΟ", "Χρυσαφί",
			"ΜΟΒ", "Μωβ");

	private static final Map<String, String> CATEGORY_KEYS = keys(CATEGORIES, TextNormalizationUtils::normalizePlate);
	private static final Map<String, String> COLOR_KEYS = colorKeys();

	// After normalizePlate: "EURO6DTEMP" -> Euro 6. A letter must follow the
	// number, so "EURO61" is not Euro 6.
	private static final Pattern EURO = Pattern.compile("EURO([1-6])(?:[A-Z][A-Z0-9]*)?");
	// "ΛΕΥΚΟ / ΜΑΥΡΟ" and "ΛΕΥΚΟ-ΜΑΥΡΟ" are one pair of colours.
	private static final Pattern COLOR_SEPARATOR = Pattern.compile("\\s*[-/]\\s*");

	private VehicleValues() {
	}

	/**
	 * A value as the import stores it (decision 6).
	 *
	 * @param value  the list's own spelling when it matched, otherwise the value as it came
	 * @param listed whether it matched; if not, the import reports it
	 */
	public record Match(String value, boolean listed) {

		static Match listed(String value) {
			return new Match(value, true);
		}

		static Match unlisted(String value) {
			return new Match(value, false);
		}

	}

	public static boolean isCategory(String value) {
		return CATEGORIES.contains(value);
	}

	public static boolean isEmissionStandard(String value) {
		return EMISSION_STANDARDS.contains(value);
	}

	/** One colour of the list, two different ones as "Λευκό-Μαύρο", or «Πολύχρωμο». */
	public static boolean isColor(String value) {
		if (COLORS.contains(value) || MULTICOLOURED.equals(value)) {
			return true;
		}
		String[] two = value.split(SECOND_COLOR, -1);
		return two.length == 2 && COLORS.contains(two[0]) && COLORS.contains(two[1]) && !two[0].equals(two[1]);
	}

	/**
	 * Compared as plates are (SPEC §6), so «Μ1» with a Greek Μ is M1, and «l3e»
	 * is L3e. «Ι.Χ.» is a use, not a category, and matches nothing: it may be
	 * M1 or N1 (decision 6).
	 */
	public static Match category(String text) {
		String category = CATEGORY_KEYS.get(TextNormalizationUtils.normalizePlate(text.strip()));
		return category != null ? Match.listed(category) : Match.unlisted(text);
	}

	/** «EURO 5» is Euro 5, and a variant with a letter is its number: «Euro 6d-TEMP» is Euro 6. */
	public static Match emissionStandard(String text) {
		String key = TextNormalizationUtils.normalizePlate(text.strip());
		if (key.equals("ZEV")) {
			return Match.listed("ZEV");
		}
		Matcher euro = EURO.matcher(key);
		return euro.matches() ? Match.listed("Euro " + euro.group(1)) : Match.unlisted(text);
	}

	/**
	 * One colour, «Πολύχρωμο», or two different colours separated by a dash or
	 * a slash. Three or more stay as they came: which one is «Πολύχρωμο» is
	 * the clerk's call.
	 */
	public static Match color(String text) {
		String key = COLOR_SEPARATOR.matcher(TextNormalizationUtils.normalizeText(text.strip())).replaceAll(SECOND_COLOR);
		if (key.equals(TextNormalizationUtils.normalizeText(MULTICOLOURED))) {
			return Match.listed(MULTICOLOURED);
		}
		String[] parts = key.split(SECOND_COLOR, -1);
		if (parts.length == 1 && COLOR_KEYS.containsKey(key)) {
			return Match.listed(COLOR_KEYS.get(key));
		}
		if (parts.length == 2) {
			String first = COLOR_KEYS.get(parts[0]);
			String second = COLOR_KEYS.get(parts[1]);
			if (first != null && second != null && !first.equals(second)) {
				return Match.listed(first + SECOND_COLOR + second);
			}
		}
		return Match.unlisted(text);
	}

	/**
	 * The brands of {@code vehicle_brand} (decision 4), read by
	 * {@link VehicleService#brands()}. Compared case and accents aside, through
	 * each brand's synonyms.
	 */
	public static final class Brands {

		private final Set<String> names;
		private final Map<String, String> byKey = new HashMap<>();

		/**
		 * @param synonymsByName each brand's name and its synonyms
		 * @throws IllegalStateException if two brands share a spelling: a
		 *             migration mistake, which would make a value mean two brands
		 */
		public Brands(Map<String, ? extends Collection<String>> synonymsByName) {
			names = Set.copyOf(synonymsByName.keySet());
			synonymsByName.forEach((name, synonyms) -> {
				key(name, name);
				synonyms.forEach(synonym -> key(synonym, name));
			});
		}

		/** The form's rule: one of the brands, written as the list writes it. */
		public boolean contains(String value) {
			return names.contains(value);
		}

		/** The import's mapping: «VW», «V.W.» and «volkswagen» are Volkswagen. */
		public Match match(String text) {
			String name = byKey.get(TextNormalizationUtils.normalizeText(text.strip()));
			return name != null ? Match.listed(name) : Match.unlisted(text);
		}

		private void key(String spelling, String name) {
			String other = byKey.putIfAbsent(TextNormalizationUtils.normalizeText(spelling), name);
			if (other != null && !other.equals(name)) {
				throw new IllegalStateException("vehicle_brand: «" + spelling + "» names both " + other + " and "
						+ name);
			}
		}

	}

	private static Map<String, String> keys(List<String> values, UnaryOperator<String> key) {
		Map<String, String> keys = new HashMap<>();
		values.forEach(value -> keys.put(key.apply(value), value));
		return Map.copyOf(keys);
	}

	private static Map<String, String> colorKeys() {
		Map<String, String> keys = new HashMap<>(keys(COLORS, TextNormalizationUtils::normalizeText));
		COLOR_SYNONYMS.forEach((synonym, color) -> keys.put(TextNormalizationUtils.normalizeText(synonym), color));
		return Map.copyOf(keys);
	}

}
