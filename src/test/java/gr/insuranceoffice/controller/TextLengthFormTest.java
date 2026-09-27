package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Task 28: every text field of the customer, vehicle and policy forms takes
 * as many characters as its column and not one more. One more gives a
 * message beside the field and the form as typed, never the error page. The
 * limits are typed out from DATA_MODEL rather than read from ColumnLimits, so
 * a wrong limit there fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class TextLengthFormTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

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

	@ParameterizedTest(name = "{0}: {2}")
	@CsvSource({ "lastName, last_name, 100", "firstName, first_name, 100", "fatherName, father_name, 100",
			"taxOffice, tax_office, 100", "street, street, 200", "city, city, 100", "email, email, 255" })
	void customerFieldTakesItsColumnLengthAndNotOneMore(String field, String column, int limit) throws Exception {
		MultiValueMap<String, String> values = customer();

		values.set(field, text(field, limit + 1));
		refused("/customers", values, field, limit, "customer-form");
		assertThat(customerRepository.count()).isZero();

		values.set(field, text(field, limit));
		saved("/customers", values);
		assertThat(jdbcTemplate.queryForObject("SELECT " + column + " FROM customer", String.class))
				.isEqualTo(text(field, limit));
	}

	@ParameterizedTest(name = "{0}: {2}")
	@CsvSource({ "plate, plate, 10", "model, model, 100", "engineNumber, engine_number, 50",
			"licenseStreet, license_street, 200", "licenseCity, license_city, 100" })
	void vehicleFieldTakesItsColumnLengthAndNotOneMore(String field, String column, int limit) throws Exception {
		MultiValueMap<String, String> values = vehicle();

		values.set(field, text(field, limit + 1));
		refused("/vehicles", values, field, limit, "vehicle-form");
		assertThat(vehicleRepository.count()).isZero();

		values.set(field, text(field, limit));
		saved("/vehicles", values);
		assertThat(jdbcTemplate.queryForObject("SELECT " + column + " FROM vehicle", String.class))
				.isEqualTo(text(field, limit));
	}

	// Brand, category, colour and Euro take only their lists' values (Task
	// 23a), so a value as long as the column comes only from the import. It
	// is kept while left as it is; a longer one typed in gets the list's
	// message beside the field, not an error page.
	@ParameterizedTest(name = "{0}: {2}")
	@CsvSource({ "brand, brand, 50, Επιλέξτε μάρκα από τη λίστα.",
			"category, category, 10, Επιλέξτε κατηγορία από τη λίστα.",
			"color, color, 50, Επιλέξτε χρώμα από τη λίστα.",
			"emissionStandard, emission_standard, 20, Επιλέξτε Euro από τη λίστα." })
	void listFieldKeepsAnImportedValueAsLongAsItsColumn(String field, String column, int limit, String listMessage)
			throws Exception {
		saved("/vehicles", vehicle());
		Long id = vehicleRepository.findAll().getFirst().getId();
		// As the import stores a value outside its list, without the form.
		jdbcTemplate.update("UPDATE vehicle SET " + column + " = ?", text(field, limit));
		MultiValueMap<String, String> values = vehicle();
		values.set("id", id.toString());
		values.set("version", "0");

		values.set(field, text(field, limit + 1));
		String html = mockMvc.perform(post("/vehicles/" + id).with(csrf()).params(values))
				.andExpect(status().isOk())
				.andExpect(view().name("vehicle-form"))
				.andReturn().getResponse().getContentAsString();
		assertThat(input(html, field)).contains("is-invalid", "value=\"" + text(field, limit + 1) + "\"");
		assertThat(message(html, field)).isEqualTo(listMessage);

		values.set(field, text(field, limit));
		values.set("model", "Golf Variant");
		saved("/vehicles/" + id, values);
		assertThat(jdbcTemplate.queryForObject("SELECT " + column + " FROM vehicle", String.class))
				.isEqualTo(text(field, limit));
	}

	// The policy number used to have a check of its own; now the common one.
	@ParameterizedTest(name = "{0}: {2}")
	@CsvSource({ "policyNumber, policy_number, 30", "insuranceCompany, insurance_company, 100" })
	void policyFieldTakesItsColumnLengthAndNotOneMore(String field, String column, int limit) throws Exception {
		Vehicle insured = insuredVehicle();
		String url = "/vehicles/" + insured.getId() + "/policies";
		MultiValueMap<String, String> values = policy(insured);

		values.set(field, text(field, limit + 1));
		refused(url, values, field, limit, "policy-form");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM policy", Long.class)).isZero();

		values.set(field, text(field, limit));
		saved(url, values);
		assertThat(jdbcTemplate.queryForObject("SELECT " + column + " FROM policy", String.class))
				.isEqualTo(text(field, limit));
	}

	// Measured as stored (Task 14): eleven typed with the dash are ten.
	@Test
	void measuresThePlateWithoutItsDash() throws Exception {
		MultiValueMap<String, String> values = vehicle();

		values.set("plate", "ΑΒΓ-12345678");
		String html = refused("/vehicles", values, "plate", 10, "vehicle-form");
		assertThat(html).contains("(γράφτηκαν 11).");

		values.set("plate", "ΑΒΓ-1234567");
		saved("/vehicles", values);
		assertThat(jdbcTemplate.queryForObject("SELECT plate FROM vehicle", String.class)).isEqualTo("ΑΒΓ1234567");
	}

	// Two chars in Java, one character in PostgreSQL.
	@Test
	void countsACharacterOutsideTheBmpAsOne() throws Exception {
		MultiValueMap<String, String> values = customer();

		values.set("lastName", "𝔸".repeat(101));
		String html = refused("/customers", values, "lastName", 100, "customer-form");
		assertThat(html).contains("(γράφτηκαν 101).");

		values.set("lastName", "𝔸".repeat(100));
		saved("/customers", values);
		assertThat(jdbcTemplate.queryForObject("SELECT char_length(last_name) FROM customer", Integer.class))
				.isEqualTo(100);
	}

	// The spaces around a value are not stored, so they do not count.
	@Test
	void measuresTheValueWithoutTheSpacesAroundIt() throws Exception {
		MultiValueMap<String, String> values = customer();
		values.set("city", "  " + text("city", 100) + "  ");

		saved("/customers", values);

		assertThat(jdbcTemplate.queryForObject("SELECT city FROM customer", String.class))
				.isEqualTo(text("city", 100));
	}

	// Every field too long is shown at once, as with any other rule.
	@Test
	void showsEveryFieldTooLongAtOnce() throws Exception {
		MultiValueMap<String, String> values = vehicle();
		values.set("model", text("model", 101));
		values.set("licenseStreet", text("licenseStreet", 205));

		String html = refused("/vehicles", values, "model", 100, "vehicle-form");

		assertThat(message(html, "licenseStreet")).isEqualTo("Έως 200 χαρακτήρες (γράφτηκαν 205).");
	}

	@Test
	void refusesATooLongValueOnEditToo() throws Exception {
		saved("/customers", customer());
		Long id = customerRepository.findAll().getFirst().getId();
		MultiValueMap<String, String> values = customer();
		values.set("id", id.toString());
		values.set("version", "0");
		values.set("street", text("street", 201));

		refused("/customers/" + id, values, "street", 200, "customer-form");

		assertThat(jdbcTemplate.queryForObject("SELECT street FROM customer", String.class)).isNull();
	}

	/**
	 * The form again, not the error page, with the message beside the field
	 * and what was typed still in it.
	 */
	private String refused(String url, MultiValueMap<String, String> values, String field, int limit, String form)
			throws Exception {
		String typed = values.getFirst(field);
		String html = mockMvc.perform(post(url).with(csrf()).params(values))
				.andExpect(status().isOk())
				.andExpect(view().name(form))
				.andReturn().getResponse().getContentAsString();
		assertThat(input(html, field)).contains("is-invalid", "value=\"" + typed + "\"");
		assertThat(message(html, field)).startsWith("Έως " + limit + " χαρακτήρες (γράφτηκαν ");
		return html;
	}

	private void saved(String url, MultiValueMap<String, String> values) throws Exception {
		mockMvc.perform(post(url).with(csrf()).params(values)).andExpect(status().is3xxRedirection());
	}

	/**
	 * Greek letters, accented ones included; the email keeps its shape, and
	 * the plate is in capitals without accents, as it is stored.
	 */
	private static String text(String field, int length) {
		return switch (field) {
			case "email" -> letters("αλεξίου", length - 5) + "@x.gr";
			case "plate" -> letters("ΑΒΓΔΕΖΗΘ", length);
			default -> letters("Αλεξίου", length);
		};
	}

	private static String letters(String pattern, int length) {
		return pattern.repeat(length / pattern.length() + 1).substring(0, length);
	}

	/** The input tag of one field. */
	private static String input(String html, String name) {
		Matcher input = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>").matcher(html);
		assertThat(input.find()).as(name).isTrue();
		return input.group();
	}

	/** The message right after the field's input, before any other field. */
	private static String message(String html, String name) {
		Matcher message = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>"
				+ "(?:(?!<input|<select|<textarea).)*?<div class=\"invalid-feedback\">([^<]*)</div>", Pattern.DOTALL)
				.matcher(html);
		assertThat(message.find()).as("message beside " + name).isTrue();
		return message.group(1);
	}

	private static MultiValueMap<String, String> customer() {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("lastName", "Αλεξίου");
		values.set("firstName", "Μαρία");
		values.set("entityType", "INDIVIDUAL");
		return values;
	}

	private static MultiValueMap<String, String> vehicle() {
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

	// A six-month policy that has ended, so the mobile rule does not apply.
	private static MultiValueMap<String, String> policy(Vehicle vehicle) {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("vehicleId", vehicle.getId().toString());
		values.set("policyNumber", "2100000001");
		values.set("insuranceCompany", "Northwind");
		values.set("startDate", "2025-03-01");
		values.set("endDate", "2025-09-01");
		values.set("premium", "180,50");
		return values;
	}

	private Vehicle insuredVehicle() {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW123456");
		vehicle.setPlate("ΑΒΕ-1234");
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		vehicleRepository.save(vehicle);

		Customer owner = new Customer();
		owner.setLastName("Αλεξίου");
		owner.setMobile("6900000001");
		customerRepository.save(owner);
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(owner);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownershipRepository.save(ownership);
		return vehicle;
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
