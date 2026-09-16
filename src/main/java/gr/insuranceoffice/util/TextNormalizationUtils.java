package gr.insuranceoffice.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The single Java implementation of text and plate normalization, shared by
 * the entity write path and the search read path.
 */
public final class TextNormalizationUtils {

	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

	// Mirrors SQL rtrim(): trailing spaces only, never leading ones and never
	// tabs or newlines. \z rather than $ so a trailing newline is kept, as in SQL.
	private static final Pattern TRAILING_SPACES = Pattern.compile(" +\\z");

	private TextNormalizationUtils() {
	}

	/**
	 * Removes accents, strips trailing spaces and upper-cases: "Αλεξίου",
	 * "αλεξιου", "ΑΛΕΞΙΟΥ" and "Αλεξίου " all become "ΑΛΕΞΙΟΥ".
	 * <p>
	 * Used on search input so it matches {@code search_normalized}, which
	 * PostgreSQL computes with {@code upper(immutable_unaccent(rtrim(...)))}
	 * (V2 migration). The trailing-space handling mirrors SQL {@code rtrim()}
	 * exactly: leading spaces are kept, and so are trailing tabs and newlines,
	 * because {@code rtrim()} strips only spaces. Both sides must give the same
	 * result;
	 * {@code SearchNormalizationConsistencyTest} checks that they do. Known gap:
	 * letters that Unicode does not decompose (ø, æ, œ, ł) are expanded by
	 * {@code unaccent} but left as they are here.
	 */
	public static String normalizeText(String input) {
		if (input == null) {
			return null;
		}
		// rtrim first, like the SQL expression does, before unaccent and upper.
		String trimmed = TRAILING_SPACES.matcher(input).replaceAll("");
		String decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
		String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
		return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
	}

	/**
	 * Normalizes a licence plate for {@code plate_normalized} and for plate
	 * lookups: accents removed, upper-cased, dashes and spaces removed, and
	 * Greek letters that look like Latin letters replaced by the Latin ones.
	 * "ΝΖΑ-8812", "nza 8812" and "NZA8812" all become "NZA8812".
	 */
	public static String normalizePlate(String plate) {
		if (plate == null) {
			return null;
		}
		String text = normalizeText(plate);
		StringBuilder normalized = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (!isSeparator(c)) {
				normalized.append(toLatinLookalike(c));
			}
		}
		return normalized.toString();
	}

	private static boolean isSeparator(char c) {
		return Character.isWhitespace(c)
				|| Character.isSpaceChar(c)
				|| Character.getType(c) == Character.DASH_PUNCTUATION;
	}

	// Greek capitals that look identical to a Latin capital (SPEC §6).
	// Escapes are used because the two alphabets are indistinguishable in source.
	private static char toLatinLookalike(char c) {
		return switch (c) {
			case 'Α' -> 'A'; // Alpha
			case 'Β' -> 'B'; // Beta
			case 'Ε' -> 'E'; // Epsilon
			case 'Ζ' -> 'Z'; // Zeta
			case 'Η' -> 'H'; // Eta
			case 'Ι' -> 'I'; // Iota
			case 'Κ' -> 'K'; // Kappa
			case 'Μ' -> 'M'; // Mu
			case 'Ν' -> 'N'; // Nu
			case 'Ο' -> 'O'; // Omicron
			case 'Ρ' -> 'P'; // Rho
			case 'Τ' -> 'T'; // Tau
			case 'Υ' -> 'Y'; // Upsilon
			case 'Χ' -> 'X'; // Chi
			default -> c;
		};
	}

}
