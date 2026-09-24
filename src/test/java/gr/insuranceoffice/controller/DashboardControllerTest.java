package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.DashboardDto;
import gr.insuranceoffice.dto.ExpiryPeriod;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/** The home page as HTML. The period rules are in DashboardServiceTest. */
@SpringBootTest
@AutoConfigureMockMvc
// Every page needs a logged-in user from Task 10 on; the login itself is in
// LoginTest.
@WithMockUser
@Import(TestcontainersConfiguration.class)
class DashboardControllerTest {

	private static final LocalDate TODAY = LocalDate.now();

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	private PolicyRepository policyRepository;

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
	void opensOnTheNextThirtyDaysAsAnHtmlTable() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW000001");
		owns(vehicle, customer("Αλεξίου", "Μαρία", "6900000001"));
		policy(vehicle, "2100000001", TODAY.plusDays(12), "Northwind", "1234.50");

		MvcResult result = mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(view().name("dashboard"))
				.andReturn();

		assertThat(dashboard(result).period()).isEqualTo(ExpiryPeriod.DAYS_30);
		String html = result.getResponse().getContentAsString();
		assertThat(html).contains("<html lang=\"el\"", "Λήξεις συμβολαίων",
				"<th scope=\"col\">Πινακίδα</th>", "<th scope=\"col\">Πελάτης</th>", "<th scope=\"col\">Κινητό</th>",
				"<th scope=\"col\">Λήξη</th>", "<th scope=\"col\">Ασφαλιστική</th>", "Ασφάλιστρο</th>",
				"ΑΒΕ1234", "Αλεξίου Μαρία", "6900000001", TODAY.plusDays(12).format(GREEK_DATE), "Northwind",
				// Greek number format.
				"1.234,50 €");
		assertThat(html).containsPattern("id=\"period-30\"[^>]*checked");
	}

	@Test
	void listsTheSoonestExpiryFirst() throws Exception {
		policy(vehicle("ΚΜΝ-2000", "WVWZZZ1KZAW000002"), "2100000002", TODAY.plusDays(20), "ACME", "150.00");
		policy(vehicle("ΚΜΝ-1000", "WVWZZZ1KZAW000001"), "2100000001", TODAY.plusDays(2), "ACME", "150.00");
		policy(vehicle("ΚΜΝ-3000", "WVWZZZ1KZAW000003"), "2100000003", TODAY.plusDays(9), "ACME", "150.00");

		String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

		assertThat(html.indexOf("ΚΜΝ1000")).isPositive()
				.isLessThan(html.indexOf("ΚΜΝ3000"));
		assertThat(html.indexOf("ΚΜΝ3000")).isLessThan(html.indexOf("ΚΜΝ2000"));
	}

	@Test
	void appliesThePeriodAndInsuranceCompanyFromTheUrl() throws Exception {
		policy(vehicle("ΚΜΝ-1000", "WVWZZZ1KZAW000001"), "2100000001", TODAY.plusDays(45), "ACME", "150.00");
		policy(vehicle("ΚΜΝ-2000", "WVWZZZ1KZAW000002"), "2100000002", TODAY.plusDays(50), "Contoso", "150.00");
		policy(vehicle("ΚΜΝ-3000", "WVWZZZ1KZAW000003"), "2100000003", TODAY.minusDays(3), "ACME", "150.00");

		String in30 = html(get("/"));
		assertThat(in30).doesNotContain("ΚΜΝ1000", "ΚΜΝ2000", "ΚΜΝ3000").contains("Κανένα συμβόλαιο");

		String in60 = html(get("/").param("period", "60"));
		assertThat(in60).contains("ΚΜΝ1000", "ΚΜΝ2000").doesNotContain("ΚΜΝ3000");

		String acmeIn60 = html(get("/").param("period", "60").param("insuranceCompany", "ACME"));
		assertThat(acmeIn60).contains("ΚΜΝ1000").doesNotContain("ΚΜΝ2000");
		assertThat(acmeIn60).containsPattern("<option value=\"ACME\"[^>]*selected");

		String expired = html(get("/").param("period", "expired"));
		assertThat(expired).contains("ΚΜΝ3000", "Έληξαν από").doesNotContain("ΚΜΝ1000");
	}

	@Test
	void fallsBackToThirtyDaysForAnUnknownPeriod() throws Exception {
		MvcResult result = mockMvc.perform(get("/").param("period", "365")).andExpect(status().isOk()).andReturn();

		assertThat(dashboard(result).period()).isEqualTo(ExpiryPeriod.DAYS_30);
	}

	// Task 16d-2: the home screen is for calling about renewals; from a phone,
	// one tap.
	@Test
	void linksEachMobileForCalling() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW000001");
		owns(vehicle, customer("Αλεξίου", "Μαρία", "6900000001"));
		policy(vehicle, "2100000001", TODAY.plusDays(12), "Northwind", "180.00");

		assertThat(html(get("/"))).contains("<a href=\"tel:6900000001\">6900000001</a>");
	}

	// DECISIONS §1: the clerk sees at once whom they cannot call.
	@Test
	void flagsAMissingMobile() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW000001");
		owns(vehicle, customer("Αλεξίου", "Μαρία", null));
		policy(vehicle, "2100000001", TODAY.plusDays(5), "Northwind", "180.00");

		assertThat(html(get("/"))).contains("Αλεξίου Μαρία", "Λείπει");
	}

	// Bootstrap comes from the webjar, not a CDN: the stylesheet the page
	// links to is served by the application itself.
	@Test
	void servesTheBootstrapStylesheetThePageLinksTo() throws Exception {
		Matcher link = Pattern.compile("<link rel=\"stylesheet\" href=\"(/webjars/bootstrap/[^\"]*bootstrap\\.min\\.css)\"")
				.matcher(html(get("/")));
		assertThat(link.find()).as("stylesheet link").isTrue();

		mockMvc.perform(get(link.group(1)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	private String html(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static DashboardDto dashboard(MvcResult result) {
		return (DashboardDto) result.getModelAndView().getModel().get("dashboard");
	}

	private Vehicle vehicle(String plate, String vin) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private Customer customer(String lastName, String firstName, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setMobile(mobile);
		return customerRepository.save(customer);
	}

	private void owns(Vehicle vehicle, Customer customer) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownershipRepository.save(ownership);
	}

	private void policy(Vehicle vehicle, String policyNumber, LocalDate endDate, String insuranceCompany,
			String premium) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany(insuranceCompany);
		policy.setStartDate(endDate.minusYears(1));
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal(premium));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
