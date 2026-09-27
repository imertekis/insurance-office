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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.VehicleValues;

/**
 * Task 23b: the vehicle form chooses category, colour and Euro from their
 * lists, searches the brand in the list of the page, and suggests the
 * models stored for the brand; a value from before the lists opens, shows,
 * and saves as it is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class VehicleFormListsTest {

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
	void choosesCategoryColourAndEuroFromTheirLists() throws Exception {
		String html = html("/vehicles/new");

		assertThat(options(html, "category")).containsExactlyElementsOf(withEmpty(VehicleValues.CATEGORIES));
		List<String> colours = new ArrayList<>(VehicleValues.COLORS);
		colours.add("Πολύχρωμο");
		assertThat(options(html, "color")).containsExactlyElementsOf(withEmpty(colours));
		assertThat(options(html, "secondColor")).containsExactlyElementsOf(withEmpty(VehicleValues.COLORS));
		assertThat(options(html, "emissionStandard"))
				.containsExactlyElementsOf(withEmpty(VehicleValues.EMISSION_STANDARDS));
		// The empty first choice, selected for a new vehicle.
		assertThat(html).containsPattern("<select[^>]*name=\"category\"[^>]*>\\s*<option value=\"\">— Επιλέξτε —</option>");
		assertThat(html).doesNotContain("εκτός λίστας");
		assertThat(labels(html)).contains("Κατηγορία (J) *", "Χρώμα (R) *", "Δεύτερο χρώμα", "Euro (V.9)");
	}

	// Searched by app.js, in the page: no request to the server.
	@Test
	void holdsTheBrandsOfTheListInThePage() throws Exception {
		String html = html("/vehicles/new");

		Matcher brands = Pattern.compile("<div class=\"dropdown-item\" role=\"option\" aria-selected=\"false\"\\s+"
				+ "id=\"brand-option-\\d+\">([^<]+)</div>").matcher(html);
		List<String> names = new ArrayList<>();
		while (brands.find()) {
			names.add(brands.group(1));
		}
		assertThat(names).hasSize(100).contains("Volkswagen", "Mercedes-Benz", "Tesla", "Yamaha");
		// In the alphabet's order, accents aside: Škoda among the S.
		assertThat(names.subList(names.indexOf("Saab"), names.indexOf("Saab") + 4))
				.containsExactly("Saab", "SEAT", "Škoda", "Smart");
		assertThat(html).contains("id=\"brand-options\" role=\"listbox\" aria-label=\"Μάρκες\"");
	}

	// Without JavaScript the brand is a text field; app.js gives the roles.
	@Test
	void leavesTheBrandATextFieldUntilTheScriptRuns() throws Exception {
		String brand = input(html("/vehicles/new"), "brand");

		assertThat(brand).contains("type=\"text\"", "autocomplete=\"off\"", "data-brand-options=\"brand-options\"",
				"data-models-for=\"model\"").doesNotContain("role=", "list=");
	}

	// Decision 5: the models stored, by brand, for the model field's datalist.
	@Test
	void holdsTheModelsStoredForEachBrand() throws Exception {
		stored("WVWZZZ1KZAW000001", "ΑΒΕ1001", "Volkswagen", "Golf", "Λευκό");
		stored("WVWZZZ1KZAW000002", "ΑΒΕ1002", "Volkswagen", "Polo", "Λευκό");
		stored("WVWZZZ1KZAW000003", "ΑΒΕ1003", "Volkswagen", "Golf", "Μαύρο");
		stored("JTDZZZ1KZAW000004", "ΑΒΕ1004", "Toyota", "Yaris", "Λευκό");

		String html = html("/vehicles/new");

		assertThat(datalist(html, "Toyota")).containsExactly("Yaris");
		assertThat(datalist(html, "Volkswagen")).containsExactly("Golf", "Polo");
		assertThat(html).doesNotContain("data-brand=\"Tesla\"");
	}

	// Task 23a, decision 7: the form offers the old value first, selected,
	// with a note, and saves it again as it is.
	@Test
	void opensShowsAndSavesAValueFromBeforeTheLists() throws Exception {
		Vehicle old = stored("WVWZZZ1KZAW000001", "ΑΒΕ1001", "ΦΙΑΤ", "Punto", "ΛΑΔΙ");
		old.setCategory("Ι.Χ.");
		old.setEmissionStandard("Euro 6d-TEMP");
		vehicleRepository.saveAndFlush(old);

		String html = html("/vehicles/" + old.getId() + "/edit");

		for (String[] field : new String[][] { { "category", "Ι.Χ." }, { "color", "ΛΑΔΙ" },
				{ "emissionStandard", "Euro 6d-TEMP" } }) {
			assertThat(select(html, field[0])).as(field[0]).contains("aria-describedby=\"" + field[0] + "-outside\"");
			assertThat(html).as(field[0]).containsPattern("<select[^>]*name=\"" + field[0] + "\"[^>]*>\\s*"
					+ "<option value=\"" + Pattern.quote(field[1]) + "\" selected>" + Pattern.quote(field[1])
					+ " \\(εκτός λίστας\\)</option>\\s*<option value=\"\">— Επιλέξτε —</option>");
			assertThat(html).as(field[0]).containsPattern("id=\"" + field[0] + "-outside\">\\s*Εκτός λίστας\\.");
		}
		assertThat(input(html, "brand")).contains("value=\"ΦΙΑΤ\"", "aria-describedby=\"brand-outside\"");
		assertThat(html).contains("id=\"brand-outside\"");

		// As a browser sends the form back, untouched.
		MultiValueMap<String, String> values = valid();
		values.set("id", old.getId().toString());
		values.set("version", "1");
		values.set("brand", "ΦΙΑΤ");
		values.set("model", "Punto");
		values.set("category", "Ι.Χ.");
		values.set("color", "ΛΑΔΙ");
		values.set("secondColor", "");
		values.set("emissionStandard", "Euro 6d-TEMP");
		mockMvc.perform(post("/vehicles/" + old.getId()).with(csrf()).params(values))
				.andExpect(redirectedUrl("/vehicles/" + old.getId()));

		assertThat(vehicleRepository.findById(old.getId())).get()
				.extracting(Vehicle::getBrand, Vehicle::getCategory, Vehicle::getColor, Vehicle::getEmissionStandard)
				.containsExactly("ΦΙΑΤ", "Ι.Χ.", "ΛΑΔΙ", "Euro 6d-TEMP");
	}

	// Decision 3: two colours, one column; the form shows them as two choices.
	@Test
	void storesTwoColoursInOneColumnAndShowsThemAsTwoChoices() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("color", "Λευκό");
		values.set("secondColor", "Μαύρο");
		mockMvc.perform(post("/vehicles").with(csrf()).params(values)).andExpect(status().is3xxRedirection());
		Vehicle stored = vehicleRepository.findByVin("WVWZZZ1KZAW123456").orElseThrow();
		assertThat(stored.getColor()).isEqualTo("Λευκό-Μαύρο");

		String html = html("/vehicles/" + stored.getId() + "/edit");
		assertThat(html).containsPattern("(?s)<select[^>]*name=\"color\".*?<option value=\"Λευκό\" selected=\"selected\">"
				+ "Λευκό</option>.*?</select>");
		assertThat(html).containsPattern("(?s)<select[^>]*name=\"secondColor\".*?<option value=\"Μαύρο\" "
				+ "selected=\"selected\">Μαύρο</option>.*?</select>");
		// The card shows the colour as stored.
		assertThat(html("/vehicles/" + stored.getId())).contains("Λευκό-Μαύρο");

		values.set("id", stored.getId().toString());
		values.set("version", "0");
		values.set("secondColor", "");
		mockMvc.perform(post("/vehicles/" + stored.getId()).with(csrf()).params(values))
				.andExpect(redirectedUrl("/vehicles/" + stored.getId()));
		assertThat(vehicleRepository.findById(stored.getId())).get().extracting(Vehicle::getColor).isEqualTo("Λευκό");
	}

	@Test
	void answersASecondColourThatCannotGoWithTheFirstBesideIt() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("color", "Πολύχρωμο");
		values.set("secondColor", "Μαύρο");

		String html = mockMvc.perform(post("/vehicles").with(csrf()).params(values))
				.andExpect(status().isOk())
				.andExpect(view().name("vehicle-form"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).containsPattern("(?s)<select[^>]*is-invalid[^>]*name=\"secondColor\"[^>]*>.*?</select>\\s*"
				+ "<div class=\"invalid-feedback\">Το «Πολύχρωμο» δεν έχει δεύτερο χρώμα\\.</div>");
		assertThat(select(html, "color")).doesNotContain("is-invalid");
		assertThat(vehicleRepository.count()).isZero();
	}

	private static List<String> withEmpty(List<String> values) {
		List<String> options = new ArrayList<>(List.of(""));
		options.addAll(values);
		return options;
	}

	/** The values of a select's options, in order. */
	private static List<String> options(String html, String name) {
		Matcher select = Pattern.compile("<select[^>]*name=\"" + name + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
				.matcher(html);
		assertThat(select.find()).as(name).isTrue();
		Matcher option = Pattern.compile("<option value=\"([^\"]*)\"").matcher(select.group(1));
		List<String> values = new ArrayList<>();
		while (option.find()) {
			values.add(option.group(1));
		}
		return values;
	}

	private static List<String> datalist(String html, String brand) {
		Matcher datalist = Pattern.compile("<datalist id=\"models-\\d+\"\\s+data-brand=\"" + brand + "\">(.*?)</datalist>",
				Pattern.DOTALL).matcher(html);
		assertThat(datalist.find()).as(brand).isTrue();
		Matcher option = Pattern.compile("<option value=\"([^\"]*)\"").matcher(datalist.group(1));
		List<String> models = new ArrayList<>();
		while (option.find()) {
			models.add(option.group(1));
		}
		return models;
	}

	private static List<String> labels(String html) {
		Matcher label = Pattern.compile("<label class=\"form-label\"[^>]*>([^<]*)</label>").matcher(html);
		List<String> labels = new ArrayList<>();
		while (label.find()) {
			labels.add(label.group(1));
		}
		return labels;
	}

	private static String input(String html, String name) {
		Matcher input = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
		assertThat(input.find()).as(name).isTrue();
		return input.group();
	}

	private static String select(String html, String name) {
		Matcher select = Pattern.compile("<select[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
		assertThat(select.find()).as(name).isTrue();
		return select.group();
	}

	private String html(String url) throws Exception {
		return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Vehicle stored(String vin, String plate, String brand, String model, String color) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand(brand);
		vehicle.setModel(model);
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor(color);
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.saveAndFlush(vehicle);
	}

	private static MultiValueMap<String, String> valid() {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("plate", "ΑΒΕ-1234");
		values.set("vin", "WVWZZZ1KZAW123456");
		values.set("brand", "Volkswagen");
		values.set("model", "Golf");
		values.set("firstRegistration", "2012-05-14");
		values.set("category", "M1");
		values.set("usageType", "ΕΙΧ");
		values.set("color", "Λευκό");
		values.set("engineCc", "1598");
		values.set("powerKw", "81");
		values.set("fuelType", "ΒΕΝΖΙΝΗ");
		return values;
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
