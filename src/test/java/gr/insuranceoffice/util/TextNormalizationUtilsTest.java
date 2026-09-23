package gr.insuranceoffice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextNormalizationUtilsTest {

	@Nested
	class NormalizeText {

		@ParameterizedTest
		@ValueSource(strings = { "Αλεξίου", "αλεξιου", "ΑΛΕΞΙΟΥ", "ΑΛΕΞΊΟΥ", "αλεξίου" })
		void accentAndCaseVariantsAreEqual(String input) {
			assertThat(TextNormalizationUtils.normalizeText(input)).isEqualTo("ΑΛΕΞΙΟΥ");
		}

		@Test
		void removesTonosFromLowerAndUpperCaseVowels() {
			assertThat(TextNormalizationUtils.normalizeText("άέήίόύώ ΆΈΉΊΌΎΏ"))
					.isEqualTo("ΑΕΗΙΟΥΩ ΑΕΗΙΟΥΩ");
		}

		@Test
		void removesDialytika() {
			assertThat(TextNormalizationUtils.normalizeText("ϊΐϋΰ ΪΫ Μαΐου"))
					.isEqualTo("ΙΙΥΥ ΙΥ ΜΑΙΟΥ");
		}

		@Test
		void upperCasesFinalSigma() {
			assertThat(TextNormalizationUtils.normalizeText("Κωνσταντίνος"))
					.isEqualTo("ΚΩΝΣΤΑΝΤΙΝΟΣ");
		}

		@Test
		void partialInputIsPrefixOfFullName() {
			assertThat(TextNormalizationUtils.normalizeText("Αλεξίου"))
					.startsWith(TextNormalizationUtils.normalizeText("αλέξ"));
		}

		@Test
		void removesLatinAccents() {
			assertThat(TextNormalizationUtils.normalizeText("Müller Café")).isEqualTo("MULLER CAFE");
		}

		@Test
		void keepsDigitsPunctuationAndSpaces() {
			assertThat(TextNormalizationUtils.normalizeText("m.alexiou@example.com 6900000001"))
					.isEqualTo("M.ALEXIOU@EXAMPLE.COM 6900000001");
		}

		@Test
		void doesNotConvertGreekLettersToLatin() {
			String normalized = TextNormalizationUtils.normalizeText("Νίκος");

			assertThat(normalized).isEqualTo("ΝΙΚΟΣ");
			assertThat(normalized).matches("\\p{IsGreek}+");
		}

		@Test
		void returnsNullForNull() {
			assertThat(TextNormalizationUtils.normalizeText(null)).isNull();
		}

	}

	@Nested
	class NormalizePlate {

		@Test
		void convertsAllGreekLookalikesToLatin() {
			String greek = "ΑΒΕΖΗΙΚΜΝΟΡΤΥΧ";
			assertThat(greek).as("test input must be Greek").matches("\\p{IsGreek}+");

			String normalized = TextNormalizationUtils.normalizePlate(greek);

			assertThat(normalized).isEqualTo("ABEZHIKMNOPTYX");
			assertThat(normalized).matches("\\p{ASCII}+");
		}

		@Test
		void greekAndLatinPlatesAreEqual() {
			String greek = "ΝΖΑ-8812";
			assertThat(greek.substring(0, 3)).as("test input must be Greek").matches("\\p{IsGreek}+");

			assertThat(TextNormalizationUtils.normalizePlate(greek))
					.isEqualTo(TextNormalizationUtils.normalizePlate("NZA-8812"))
					.isEqualTo("NZA8812");
		}

		@Test
		void convertsLowerCaseGreekToLatin() {
			assertThat(TextNormalizationUtils.normalizePlate("νζα 8812")).isEqualTo("NZA8812");
		}

		@Test
		void convertsMixedGreekAndLatinToLatin() {
			// Greek Nu followed by Latin Z and A, as happens when switching keyboards mid-input.
			assertThat(TextNormalizationUtils.normalizePlate("ΝZA-8812")).isEqualTo("NZA8812");
		}

		@Test
		void keepsGreekLettersWithoutLatinLookalike() {
			// Only the 14 look-alikes are converted (CLAUDE.md, resolved conflict 5).
			// Special plates such as army plates contain Greek-only letters like Σ.
			assertThat(TextNormalizationUtils.normalizePlate("ΕΣ-12345")).isEqualTo("EΣ12345");
			assertThat(TextNormalizationUtils.normalizePlate("ΑΒΓ-1234"))
					.isEqualTo("ABΓ1234")
					.isNotEqualTo(TextNormalizationUtils.normalizePlate("ABG-1234"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "NZA8812", "NZA-8812", "NZA 8812", "nza-8812", " NZA - 8812 ",
				"NZA–8812", "NZA 8812" })
		void removesDashesAndSpaces(String input) {
			assertThat(TextNormalizationUtils.normalizePlate(input)).isEqualTo("NZA8812");
		}

		@Test
		void returnsNullForNull() {
			assertThat(TextNormalizationUtils.normalizePlate(null)).isNull();
		}

	}

	@Nested
	class StoredPlate {

		@ParameterizedTest
		@ValueSource(strings = { "ΝΚΝ-7777", "ΝΚΝ 7777", " ΝΚΝ - 7777 ", "ΝΚΝ–7777", "ΝΚΝ\u00a07777", "ΝΚΝ7777" })
		void removesDashesAndSpaces(String input) {
			assertThat(TextNormalizationUtils.storedPlate(input)).isEqualTo("ΝΚΝ7777");
		}

		// Task 17: capitals and no accents, as normalizeText gives them.
		@Test
		void upperCasesAndRemovesAccents() {
			assertThat(TextNormalizationUtils.storedPlate("νκν-1234")).isEqualTo("ΝΚΝ1234");
			assertThat(TextNormalizationUtils.storedPlate("άβε1234")).isEqualTo("ΑΒΕ1234");
			assertThat(TextNormalizationUtils.storedPlate("nza-8812")).isEqualTo("NZA8812");
		}

		// Unlike normalizePlate, which only feeds the search column: Greek
		// stays Greek, Latin stays Latin, Greek-only letters stay.
		@Test
		void keepsTheAlphabetAsTyped() {
			assertThat(TextNormalizationUtils.storedPlate("abe1234")).isEqualTo("\u0041\u0042\u0045" + "1234");
			assertThat(TextNormalizationUtils.storedPlate("αβε1234")).isEqualTo("\u0391\u0392\u0395" + "1234");
			assertThat(TextNormalizationUtils.storedPlate("ΑΒΓ-1234")).isEqualTo("ΑΒΓ1234");
			assertThat(TextNormalizationUtils.storedPlate("ΕΣ-12345")).isEqualTo("ΕΣ12345");
		}

		@Test
		void returnsNullForNull() {
			assertThat(TextNormalizationUtils.storedPlate(null)).isNull();
		}

	}

	@Nested
	class StoredVin {

		@Test
		void upperCases() {
			assertThat(TextNormalizationUtils.storedVin("wvwzzz1kzaw000001")).isEqualTo("WVWZZZ1KZAW000001");
			assertThat(TextNormalizationUtils.storedVin("WVWZZZ1KZAW000001")).isEqualTo("WVWZZZ1KZAW000001");
		}

		@Test
		void returnsNullForNull() {
			assertThat(TextNormalizationUtils.storedVin(null)).isNull();
		}

	}

}
