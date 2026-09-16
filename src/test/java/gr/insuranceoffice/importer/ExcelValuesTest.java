package gr.insuranceoffice.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.importer.ExcelValues.Surcharge;

class ExcelValuesTest {

	@Nested
	class BlankToNull {

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   ", "-", " - " })
		void treatsDashAndBlankAsNoValue(String text) {
			assertThat(ExcelValues.blankToNull(text)).isNull();
		}

		@Test
		void trimsAnyOtherText() {
			assertThat(ExcelValues.blankToNull(" Πατησίων 126 ")).isEqualTo("Πατησίων 126");
		}

	}

	@Nested
	class Money {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
				"180,00 €   | 180.00",
				"119,80 €   | 119.80",
				"1.234,56 € | 1234.56",
				"180,5      | 180.50",
				"180        | 180.00",
				"180.50     | 180.50" })
		void parsesGreekAndPlainAmountsToTwoDecimals(String text, BigDecimal expected) {
			assertThat(ExcelValues.parseMoney(text)).isEqualTo(expected);
		}

		@Test
		void ignoresTheNoBreakSpaceExcelPutsBeforeTheEuroSign() {
			assertThat(ExcelValues.parseMoney("180,00 €")).isEqualTo(new BigDecimal("180.00"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "δωρεάν", "1,234.56", "180,123 €", "-180" })
		void rejectsAnythingItWouldHaveToGuess(String text) {
			assertThatThrownBy(() -> ExcelValues.parseMoney(text))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(text);
		}

	}

	@Nested
	class Percentage {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = { "100% | 100", "50% | 50", "33,33 % | 33.33", "50 | 50" })
		void parsesWithOrWithoutThePercentSign(String text, BigDecimal expected) {
			assertThat(ExcelValues.parsePercentage(text)).isEqualByComparingTo(expected);
		}

		@Test
		void rejectsText() {
			assertThatThrownBy(() -> ExcelValues.parsePercentage("μισό"))
					.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Dates {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = { "25/02/2026 | 2026-02-25", "5/9/2020 | 2020-09-05" })
		void parsesDayMonthYear(String text, LocalDate expected) {
			assertThat(ExcelValues.parseDate(text)).isEqualTo(expected);
		}

		@ParameterizedTest
		@ValueSource(strings = { "31/02/2026", "2026-02-25", "02/25/2026", "25/02/26" })
		void rejectsImpossibleDatesAndOtherFormats(String text) {
			assertThatThrownBy(() -> ExcelValues.parseDate(text))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(text);
		}

	}

	@Nested
	class Numbers {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = { "1364 | 1364", "1.598 | 1598", "0 | 0" })
		void parsesIntegersIncludingGreekThousandsGroups(String text, int expected) {
			assertThat(ExcelValues.parseInteger(text)).isEqualTo(expected);
		}

		@ParameterizedTest
		@ValueSource(strings = { "1,5", "πέντε", "1 364" })
		void rejectsAnythingButAWholeNumber(String text) {
			assertThatThrownBy(() -> ExcelValues.parseInteger(text))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void parsesDecimalsWithACommaOrAPoint() {
			assertThat(ExcelValues.parseDecimal("103,5")).isEqualByComparingTo("103.5");
			assertThat(ExcelValues.parseDecimal("103.5")).isEqualByComparingTo("103.5");
		}

	}

	@Nested
	class Surcharges {

		@Test
		void mapsNewDriver() {
			assertThat(ExcelValues.parseSurcharge("ΝΑΙ (Ν.Ο.Δ.)"))
					.isEqualTo(new Surcharge(true, SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ));
		}

		@Test
		void mapsAge() {
			assertThat(ExcelValues.parseSurcharge("ΝΑΙ (Ε.Η.)"))
					.isEqualTo(new Surcharge(true, SurchargeType.ΗΛΙΚΙΑΣ));
		}

		@Test
		void mapsNoSurcharge() {
			assertThat(ExcelValues.parseSurcharge("ΟΧΙ")).isEqualTo(new Surcharge(false, null));
		}

		@ParameterizedTest
		@ValueSource(strings = { "Ναι (Ν.Ο.Δ.)", "ΝΑΙ(Ν.Ο.Δ.)", "ναι ( ν.ο.δ. )" })
		void ignoresCaseAccentsAndSpaces(String text) {
			assertThat(ExcelValues.parseSurcharge(text))
					.isEqualTo(new Surcharge(true, SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ));
		}

		@ParameterizedTest
		@ValueSource(strings = { "ΝΑΙ", "ΙΣΩΣ", "ΝΑΙ (Ν.Ο.)" })
		void rejectsValuesItWouldHaveToGuess(String text) {
			assertThatThrownBy(() -> ExcelValues.parseSurcharge(text))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(text);
		}

	}

	@Nested
	class Enums {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
				"ΒΕΝΖΙΝΗ     | ΒΕΝΖΙΝΗ",
				"Βενζίνη     | ΒΕΝΖΙΝΗ",
				"ηλεκτρισμός | ΗΛΕΚΤΡΙΣΜΟΣ",
				"LPG         | LPG" })
		void parsesFuelTypesIgnoringCaseAndAccents(String text, FuelType expected) {
			assertThat(ExcelValues.parseEnum(text, FuelType.class)).isEqualTo(expected);
		}

		@Test
		void parsesUsageType() {
			assertThat(ExcelValues.parseEnum("ΕΙΧ", UsageType.class)).isEqualTo(UsageType.ΕΙΧ);
		}

		@Test
		void rejectsAnUnknownValueListingTheAllowedOnes() {
			assertThatThrownBy(() -> ExcelValues.parseEnum("ΝΤΙΖΕΛ", FuelType.class))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("ΝΤΙΖΕΛ")
					.hasMessageContaining("ΠΕΤΡΕΛΑΙΟ");
		}

	}

}
