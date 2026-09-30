package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.service.VehicleValues.Brands;
import gr.insuranceoffice.service.VehicleValues.ColorChoices;
import gr.insuranceoffice.service.VehicleValues.Match;

/**
 * Task 23a: how the Excel import maps a value onto its list, and which
 * values the form takes. The brands here are a few of V7's; the whole table
 * is read in VehicleServiceTest.
 */
class VehicleValuesTest {

	private static final Brands BRANDS = new Brands(Map.of(
			"Volkswagen", List.of("VW", "V.W."),
			"Mercedes-Benz", List.of("MERCEDES", "MERCEDES BENZ"),
			"Audi", List.of(),
			"Citroën", List.of()));

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "VW, Volkswagen", "V.W., Volkswagen", "VOLKSWAGEN, Volkswagen", "volkswagen, Volkswagen",
			"AUDI, Audi", "MERCEDES, Mercedes-Benz", "mercedes benz, Mercedes-Benz", "Mercedes-Benz, Mercedes-Benz",
			"CITROEN, Citroën", "' Audi ', Audi" })
	void mapsABrandThroughCaseAccentsAndSynonyms(String text, String brand) {
		assertThat(BRANDS.match(text)).isEqualTo(new Match(brand, true));
	}

	// No guess: a spelling no brand lists stays as it came, and is reported.
	@ParameterizedTest
	@ValueSource(strings = { "VOLKS", "Mercedes Benz AG", "ΦΙΑΤ" })
	void keepsAnUnknownBrandAsItCame(String text) {
		assertThat(BRANDS.match(text)).isEqualTo(new Match(text, false));
	}

	@Test
	void refusesTwoBrandsWithTheSameSpelling() {
		assertThatThrownBy(() -> new Brands(Map.of("Volkswagen", List.of("VW"), "Volvo", List.of("vw"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("vehicle_brand");
	}

	@Test
	void takesOnlyABrandAsTheListWritesItInTheForm() {
		assertThat(BRANDS.contains("Volkswagen")).isTrue();
		assertThat(BRANDS.contains("VW")).isFalse();
		assertThat(BRANDS.contains("volkswagen")).isFalse();
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "ΑΣΠΡΟ, Λευκό", "ΜΑΥΡΟ, Μαύρο", "μαύρο, Μαύρο", "ΑΣΗΜΕΝΙΟ, Ασημί", "ΑΣΗΜΙ, Ασημί",
			"Γκρίζο, Γκρι", "ΠΟΛΥΧΡΩΜΟ, Πολύχρωμο", "ΛΕΥΚΟ-ΜΑΥΡΟ, Λευκό-Μαύρο", "ΑΣΠΡΟ / ΜΑΥΡΟ, Λευκό-Μαύρο",
			"Μαύρο-Λευκό, Μαύρο-Λευκό" })
	void mapsAColourOrAPairOfColours(String text, String colour) {
		assertThat(VehicleValues.color(text)).isEqualTo(new Match(colour, true));
	}

	// «ΔΙΧΡΩΜΟ» does not say which two; three colours are the clerk's call.
	@ParameterizedTest
	@ValueSource(strings = { "ΛΑΔΙ", "ΔΙΧΡΩΜΟ", "Λευκό-Λευκό", "Λευκό-Μαύρο-Κόκκινο", "Λευκό-", "Πολύχρωμο-Μαύρο" })
	void keepsAnUnknownColourAsItCame(String text) {
		assertThat(VehicleValues.color(text)).isEqualTo(new Match(text, false));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "M1, M1", "m1, M1", "M 1, M1", "l3e, L3e", "N3, N3", "T, T" })
	void mapsACategory(String text, String category) {
		assertThat(VehicleValues.category(text)).isEqualTo(new Match(category, true));
	}

	// Greek capitals that look like the Latin ones (SPEC §6).
	@Test
	void mapsACategoryTypedWithAGreekLetter() {
		assertThat(VehicleValues.category("Μ1")).isEqualTo(new Match("M1", true)); // Greek Mu
		assertThat(VehicleValues.category("Ο4")).isEqualTo(new Match("O4", true)); // Greek Omicron
		assertThat(VehicleValues.category("Τ")).isEqualTo(new Match("T", true)); // Greek Tau
	}

	// «Ι.Χ.» is a use: the vehicle may be M1 or N1, so it is never M1.
	@ParameterizedTest
	@ValueSource(strings = { "Ι.Χ.", "M1G", "M4", "L8e", "ΕΙΧ" })
	void keepsAnUnknownCategoryAsItCame(String text) {
		assertThat(VehicleValues.category(text)).isEqualTo(new Match(text, false));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "Euro 6d-TEMP, Euro 6", "Euro 5b, Euro 5", "EURO 6, Euro 6", "euro6, Euro 6", "Euro 6d-ISC-FCM, Euro 6",
			"Euro 1, Euro 1", "ZEV, ZEV", "zev, ZEV" })
	void mapsAnEuroVariantToItsNumber(String text, String euro) {
		assertThat(VehicleValues.emissionStandard(text)).isEqualTo(new Match(euro, true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Euro 7", "Euro 0", "Euro 61", "Euro VI", "E6", "EEV" })
	void keepsAnUnknownEuroAsItCame(String text) {
		assertThat(VehicleValues.emissionStandard(text)).isEqualTo(new Match(text, false));
	}

	// The form's rule: the list's own spelling only.
	@Test
	void takesInTheFormOnlyTheListsOwnSpelling() {
		assertThat(VehicleValues.CATEGORIES).allMatch(VehicleValues::isCategory);
		assertThat(VehicleValues.isCategory("m1")).isFalse();
		assertThat(VehicleValues.isCategory("Μ1")).isFalse();
		assertThat(VehicleValues.EMISSION_STANDARDS).allMatch(VehicleValues::isEmissionStandard);
		assertThat(VehicleValues.isEmissionStandard("Euro 6d-TEMP")).isFalse();
		assertThat(VehicleValues.COLORS).allMatch(VehicleValues::isColor);
		assertThat(VehicleValues.isColor("Πολύχρωμο")).isTrue();
		assertThat(VehicleValues.isColor("Λευκό-Μαύρο")).isTrue();
		assertThat(VehicleValues.isColor("ΜΑΥΡΟ")).isFalse();
		assertThat(VehicleValues.isColor("Λευκό-Λευκό")).isFalse();
		assertThat(VehicleValues.isColor("Λευκό / Μαύρο")).isFalse();
		assertThat(VehicleValues.isColor("Λευκό-Μαύρο-Κόκκινο")).isFalse();
	}

	// Task 23b: the form's two choices for a stored colour, and back.
	@ParameterizedTest(name = "{0} -> {1} + {2}")
	@CsvSource({ "Λευκό-Μαύρο, Λευκό, Μαύρο", "Λευκό, Λευκό, ", "Πολύχρωμο, Πολύχρωμο, ", "ΛΑΔΙ, ΛΑΔΙ, ",
			"Λευκό-ΛΑΔΙ, Λευκό-ΛΑΔΙ, ", "Λευκό-Λευκό, Λευκό-Λευκό, " })
	void splitsAStoredColourIntoTheFormsTwoChoices(String stored, String first, String second) {
		ColorChoices choices = ColorChoices.of(stored);

		assertThat(choices).isEqualTo(new ColorChoices(first, second));
		assertThat(choices.stored()).isEqualTo(stored);
	}

	@Test
	void joinsTheFormsTwoChoices() {
		assertThat(new ColorChoices("Λευκό", "Μαύρο").stored()).isEqualTo("Λευκό-Μαύρο");
		assertThat(new ColorChoices("Λευκό", null).stored()).isEqualTo("Λευκό");
		assertThat(new ColorChoices(null, "Μαύρο").stored()).isNull();
		assertThat(ColorChoices.of(null)).isEqualTo(new ColorChoices(null, null));
	}

	// Task 23, decision 3: two colours fit the colour column.
	@Test
	void fitsTheLongestPairOfColoursInItsColumn() {
		int longest = VehicleValues.COLORS.stream().mapToInt(String::length).max().orElseThrow();
		assertThat(2 * longest + 1).isLessThanOrEqualTo(ColumnLimits.of(Vehicle.class, "color"));
	}

}
