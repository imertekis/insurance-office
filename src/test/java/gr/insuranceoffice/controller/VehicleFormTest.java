package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.VehicleRepository;

/** Task 11b: creating and editing a vehicle from the UI. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class VehicleFormTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void startEmpty() {
		truncateTables();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void opensAnEmptyFormWithTheLicenceCodesAndGreekDropdowns() throws Exception {
		String html = html(get("/vehicles/new"));

		assertThat(html).contains("Νέο όχημα", "action=\"/vehicles\"",
				// The sections and codes of the card (SPEC §7.2).
				"Ταυτότητα", "Τεχνικά", "Διεύθυνση άδειας",
				"Αρ. Κυκλοφορίας (A)", "Αρ. Πλαισίου / VIN (E)", "Μάρκα (D.1)", "1η Άδεια (B)",
				"Κατηγορία (J)", "Ισχύς kW (P.2)", "Κυβικά (P.1)", "Αρ. Κινητήρα (P.5)", "Βάρος kg (G)",
				// Enum values stay Greek (DATA_MODEL).
				"<option value=\"ΕΙΧ\"", "<option value=\"ΤΑΞΙ\"", "<option value=\"ΒΕΝΖΙΝΗ\"",
				"<option value=\"ΗΛΕΚΤΡΙΣΜΟΣ\"", "<option value=\"LPG\"");
		assertThat(html(get("/"))).contains("href=\"/vehicles/new\"");
	}

	@Test
	void createsAVehicleAndLandsOnTheCard() throws Exception {
		mockMvc.perform(form("/vehicles", valid()))
				.andExpect(status().is3xxRedirection());

		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();
		assertThat(stored.getPlate()).isEqualTo("ΑΒΕ1234");
		// Filled by the entity's callback, not by the form (ARCHITECTURE §5).
		assertThat(stored.getPlateNormalized()).isEqualTo("ABE1234");
		assertThat(stored.getUsageType()).isEqualTo(UsageType.ΕΙΧ);
		assertThat(stored.getFuelType()).isEqualTo(FuelType.ΒΕΝΖΙΝΗ);
		assertThat(stored.getEngineCc()).isEqualTo(1598);
		assertThat(stored.getPowerKw()).isEqualByComparingTo("81");
		assertThat(stored.getLicenseCity()).isEqualTo("Δοκιμοχώρι");
		assertThat(stored.getVersion()).isZero();
	}

	// Task 14: no dash or space is stored.
	@Test
	void storesThePlateWithoutDashesOrSpaces() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("plate", " ΝΚΝ - 7777 ");

		mockMvc.perform(form("/vehicles", values)).andExpect(status().is3xxRedirection());

		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();
		assertThat(stored.getPlate()).isEqualTo("ΝΚΝ7777");
		assertThat(stored.getPlateNormalized()).isEqualTo("NKN7777");
		assertThat(html(get("/vehicles/{id}", stored.getId()))).contains("ΝΚΝ7777").doesNotContain("ΝΚΝ-7777");
		assertThat(html(get("/vehicles/{id}/edit", stored.getId()))).contains("value=\"ΝΚΝ7777\"");
	}

	@Test
	void treatsADashAloneAsAMissingPlate() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("plate", " - ");

		assertThat(html(form("/vehicles", values))).contains("Ο αριθμός κυκλοφορίας είναι υποχρεωτικός.");
		assertThat(vehicleRepository.count()).isZero();
	}

	@Test
	void writesTheVinInCapitals() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("vin", "wvwzzz1kzaw123456");

		mockMvc.perform(form("/vehicles", values)).andExpect(status().is3xxRedirection());

		assertThat(vehicleRepository.findByVin("WVWZZZ1KZAW123456")).isPresent();
	}

	@Test
	void refusesAVinOfTheWrongShapeOrOneAlreadyUsed() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> letterI = valid();
		letterI.set("vin", "WVWZZZ1KZIW123456");
		letterI.set("plate", "ΚΜΝ-4321");
		assertThat(html(form("/vehicles", letterI)))
				.contains("Το VIN έχει 17 χαρακτήρες, χωρίς τα γράμματα I, O και Q.");

		MultiValueMap<String, String> sameVin = valid();
		sameVin.set("plate", "ΚΜΝ-4321");
		assertThat(html(form("/vehicles", sameVin))).contains("Υπάρχει ήδη όχημα με αυτό το VIN.");
		assertThat(vehicleRepository.count()).isEqualTo(1);
	}

	// REVIEW-07 finding 4: the check sees the VIN as it would be stored.
	@Test
	void refusesAVinAlreadyUsedTypedInSmallLetters() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> sameVinSmall = valid();
		sameVinSmall.set("vin", "wvwzzz1kzaw123456");
		sameVinSmall.set("plate", "ΚΜΝ-4321");

		assertThat(html(form("/vehicles", sameVinSmall))).contains("Υπάρχει ήδη όχημα με αυτό το VIN.");
		assertThat(vehicleRepository.count()).isEqualTo(1);
	}

	// CLAUDE.md §5: ΑΒΕ-1234 and ABE-1234 are the same plate.
	@Test
	void refusesTheSamePlateWrittenInLatinLetters() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> latin = valid();
		latin.set("vin", "WVWZZZ1KZAW654321");
		latin.set("plate", "ABE 1234");

		assertThat(html(form("/vehicles", latin))).contains("Υπάρχει ήδη όχημα με αυτή την πινακίδα");
		assertThat(vehicleRepository.count()).isEqualTo(1);
	}

	// Task 17: capitals and no accents, in the alphabet it was typed in.
	@Test
	void storesThePlateInCapitalsWithoutAccentsInItsOwnAlphabet() throws Exception {
		Map<String, String> typedToStored = Map.of(
				"νκν-1234", "ΝΚΝ1234",
				"άβε5678", "ΑΒΕ" + "5678",
				"abe9876", "ABE" + "9876");
		int n = 0;
		for (Map.Entry<String, String> plate : typedToStored.entrySet()) {
			String vin = "WVWZZZ1KZAW00000" + ++n;
			MultiValueMap<String, String> values = valid();
			values.set("vin", vin);
			values.set("plate", plate.getKey());
			mockMvc.perform(form("/vehicles", values)).andExpect(status().is3xxRedirection());

			assertThat(vehicleRepository.findByVin(vin).orElseThrow().getPlate()).as(plate.getKey())
					.isEqualTo(plate.getValue());
		}
	}

	@Test
	void refusesTheSamePlateWrittenInSmallLetters() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> small = valid();
		small.set("vin", "WVWZZZ1KZAW654321");
		small.set("plate", "αβε1234");

		assertThat(html(form("/vehicles", small))).contains("Υπάρχει ήδη όχημα με αυτή την πινακίδα");
		assertThat(vehicleRepository.count()).isEqualTo(1);
	}

	// DATA_MODEL: an electric vehicle has no engine capacity.
	@Test
	void requiresTheEngineCapacityExceptForAnElectricVehicle() throws Exception {
		MultiValueMap<String, String> missing = valid();
		missing.set("engineCc", "");
		assertThat(html(form("/vehicles", missing))).contains("Τα κυβικά είναι υποχρεωτικά.");

		MultiValueMap<String, String> electricWithCc = valid();
		electricWithCc.set("fuelType", "ΗΛΕΚΤΡΙΣΜΟΣ");
		assertThat(html(form("/vehicles", electricWithCc)))
				.contains("Ηλεκτρικό όχημα δεν έχει κυβικά· αφήστε το πεδίο κενό.");

		MultiValueMap<String, String> electric = valid();
		electric.set("fuelType", "ΗΛΕΚΤΡΙΣΜΟΣ");
		electric.set("engineCc", "");
		mockMvc.perform(form("/vehicles", electric)).andExpect(status().is3xxRedirection());

		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();
		assertThat(stored.getFuelType()).isEqualTo(FuelType.ΗΛΕΚΤΡΙΣΜΟΣ);
		assertThat(stored.getEngineCc()).isNull();
	}

	@Test
	void showsEveryMissingFieldAndKeepsWhatWasTyped() throws Exception {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("plate", "ΑΒΕ-1234");
		values.set("vin", "");
		values.set("brand", "");
		values.set("model", "Golf");

		String html = html(form("/vehicles", values));

		assertThat(html).contains(
				"Ο αριθμός πλαισίου (VIN) είναι υποχρεωτικός.",
				"Η μάρκα είναι υποχρεωτική.",
				"Η ημερομηνία 1ης άδειας είναι υποχρεωτική.",
				"Επιλέξτε χρήση οχήματος.",
				"Επιλέξτε καύσιμο.",
				"Η ισχύς σε kW είναι υποχρεωτική.",
				// Nothing is retyped.
				"value=\"ΑΒΕ-1234\"", "value=\"Golf\"");
		assertThat(vehicleRepository.count()).isZero();
	}

	// A number the browser let through as text must not end in a 400 page.
	@Test
	void asksForANumberInsteadOfFailingToBind() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("weightKg", "χίλια");

		assertThat(html(form("/vehicles", values)))
				.contains("Συμπληρώστε αριθμό.", "value=\"ΑΒΕ-1234\"");
		assertThat(vehicleRepository.count()).isZero();
	}

	// Task 16d-1: every number the same way, a text field with the phone's
	// number keypad, no spinner arrows; dates show what to type.
	@Test
	void showsNumbersAsTextWithTheNumberKeypadAndDatesWithAPlaceholder() throws Exception {
		String html = html(get("/vehicles/new"));

		assertThat(html).doesNotContain("type=\"number\"");
		for (String integer : List.of("seats", "engineCc", "co2", "weightKg")) {
			assertThat(input(html, integer)).as(integer).contains("type=\"text\"", "inputmode=\"numeric\"");
		}
		assertThat(input(html, "powerKw")).contains("type=\"text\"", "inputmode=\"decimal\"");
		assertThat(input(html, "brand")).doesNotContain("inputmode");
		for (String date : List.of("firstRegistration", "licenseIssueDate")) {
			assertThat(input(html, date)).as(date).contains("js-date", "placeholder=\"ηη/μμ/εεεε\"");
		}
		// Shown in capitals while typed, as they are stored (Task 17).
		assertThat(input(html, "plate")).contains("text-uppercase");
		assertThat(input(html, "vin")).contains("text-uppercase");
		assertThat(input(html, "brand")).doesNotContain("text-uppercase");
	}

	// The Greek number keypad of a phone types a comma.
	@ParameterizedTest
	@ValueSource(strings = { "12,5", "12.5", " 12,50 " })
	void readsThePowerWithACommaOrAPoint(String typed) throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("powerKw", typed);

		mockMvc.perform(form("/vehicles", values)).andExpect(status().is3xxRedirection());

		assertThat(vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow().getPowerKw())
				.isEqualTo(new BigDecimal("12.50"));
	}

	// The browser no longer stops a word in a number field; the message is
	// beside that field.
	@ParameterizedTest
	@ValueSource(strings = { "seats", "engineCc", "powerKw", "co2", "weightKg" })
	void asksForANumberBesideTheFieldThatHasAWord(String field) throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set(field, "χίλια");

		String html = html(form("/vehicles", values));

		assertThat(input(html, field)).contains("is-invalid");
		assertThat(html).containsPattern(
				"id=\"" + field + "\"[^>]*>\\s*<div class=\"invalid-feedback\">Συμπληρώστε αριθμό\\.</div>");
		assertThat(input(html, "brand")).doesNotContain("is-invalid");
		assertThat(vehicleRepository.count()).isZero();
	}

	@Test
	void editsAnExistingVehicle() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());
		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();

		// Editing starts from the card (Task 9).
		assertThat(html(get("/vehicles/{id}", stored.getId())))
				.contains("href=\"/vehicles/" + stored.getId() + "/edit\"", "Επεξεργασία");
		assertThat(html(get("/vehicles/{id}/edit", stored.getId())))
				.contains("Επεξεργασία οχήματος", "value=\"WVWZZZ1KZAW123456\"", "value=\"ΑΒΕ1234\"",
						"name=\"version\" value=\"0\"");

		MultiValueMap<String, String> values = valid();
		values.set("id", stored.getId().toString());
		values.set("version", "0");
		values.set("color", "Μαύρο");
		mockMvc.perform(form("/vehicles/" + stored.getId(), values))
				.andExpect(redirectedUrl("/vehicles/" + stored.getId()));

		assertThat(vehicleRepository.findById(stored.getId())).get()
				.extracting(Vehicle::getColor, Vehicle::getVersion)
				.containsExactly("Μαύρο", 1L);
	}

	// A vehicle keeps its own VIN and plate when something else is edited.
	@Test
	void doesNotTakeAVehiclesOwnVinAsADuplicate() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());
		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();

		MultiValueMap<String, String> values = valid();
		values.set("id", stored.getId().toString());
		values.set("version", "0");
		values.set("seats", "4");

		mockMvc.perform(form("/vehicles/" + stored.getId(), values))
				.andExpect(redirectedUrl("/vehicles/" + stored.getId()));
		assertThat(vehicleRepository.findById(stored.getId())).get().extracting(Vehicle::getSeats)
				.isEqualTo((short) 4);
	}

	// SPEC §9: the second writer is told, and nothing is overwritten.
	@Test
	void refusesToSaveOverSomeoneElsesChange() throws Exception {
		mockMvc.perform(form("/vehicles", valid())).andExpect(status().is3xxRedirection());
		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();
		stored.setColor("Μαύρο");
		vehicleRepository.save(stored);

		MultiValueMap<String, String> values = valid();
		values.set("id", stored.getId().toString());
		values.set("version", "0");
		values.set("color", "Κόκκινο");

		String html = mockMvc.perform(form("/vehicles/" + stored.getId(), values))
				.andExpect(status().isOk())
				.andExpect(view().name("vehicle-form"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("άλλαξε από άλλον χρήστη", "value=\"Κόκκινο\"");
		assertThat(vehicleRepository.findById(stored.getId())).get().extracting(Vehicle::getColor)
				.isEqualTo("Μαύρο");
	}

	@Test
	void answers404ForAVehicleThatNoLongerExists() throws Exception {
		mockMvc.perform(get("/vehicles/{id}/edit", 999)).andExpect(status().isNotFound());
	}

	private static MultiValueMap<String, String> valid() {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("plate", "ΑΒΕ-1234");
		values.set("vin", "WVWZZZ1KZAW123456");
		values.set("brand", "Volkswagen");
		values.set("model", "Golf");
		values.set("firstRegistration", "2012-05-14");
		values.set("licenseIssueDate", "2021-11-22");
		values.set("category", "M1");
		values.set("usageType", "ΕΙΧ");
		values.set("color", "Λευκό");
		values.set("seats", "5");
		values.set("engineCc", "1598");
		values.set("powerKw", "81");
		values.set("fuelType", "ΒΕΝΖΙΝΗ");
		values.set("engineNumber", "ENG0001");
		values.set("co2", "120");
		values.set("emissionStandard", "Euro 6");
		values.set("weightKg", "1250");
		values.set("licenseStreet", "Οδός Άδειας 10");
		values.set("licenseCity", "Δοκιμοχώρι");
		values.set("licensePostalCode", "99100");
		return values;
	}

	private static MockHttpServletRequestBuilder form(String url, MultiValueMap<String, String> values) {
		return post(url).with(csrf()).params(values);
	}

	/** The input tag of one field, as rendered. */
	private static String input(String html, String name) {
		Matcher input = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
		assertThat(input.find()).as(name).isTrue();
		return input.group();
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
