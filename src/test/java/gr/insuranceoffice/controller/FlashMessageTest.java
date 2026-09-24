package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
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

/**
 * Task 16e: every save, deletion and renewal says so on the page it lands
 * on, once. Deleting is the ΔΙΑΧΕΙΡΙΣΤΗΣ's (Task 11e), so the user here is one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ΔΙΑΧΕΙΡΙΣΤΗΣ")
@Import(TestcontainersConfiguration.class)
class FlashMessageTest {

	private static final LocalDate TODAY = LocalDate.now();

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

	private Customer maria;

	private Vehicle vehicle;

	private Policy policy;

	@BeforeEach
	void startWithAnInsuredVehicle() {
		truncateTables();
		maria = customer("Αλεξίου", "6900000001");
		vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		owns(vehicle, maria, null);
		policy = policy(vehicle, "2100000001", TODAY.minusMonths(6), TODAY.plusMonths(6));
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void savesANewCustomer() throws Exception {
		landsWith(post("/customers").with(csrf())
				.param("lastName", "Βασιλείου").param("entityType", "INDIVIDUAL").param("taxId", "900000080"),
				"Αποθηκεύτηκε.");
	}

	// DECISIONS §2: the missing ΑΦΜ is still said, beside «Αποθηκεύτηκε».
	@Test
	void keepsTheMissingTaxIdWarningBesideTheNotice() throws Exception {
		String html = landsWith(post("/customers").with(csrf())
				.param("lastName", "Βασιλείου").param("entityType", "INDIVIDUAL"), "Αποθηκεύτηκε.");

		assertThat(flash(html)).contains("alert alert-warning", "Λείπει το ΑΦΜ του πελάτη.");
		assertThat(flash(html).indexOf("Αποθηκεύτηκε.")).isLessThan(flash(html).indexOf("Λείπει το ΑΦΜ"));
	}

	@Test
	void savesAnEditedCustomer() throws Exception {
		landsWith(post("/customers/{id}", maria.getId()).with(csrf())
				.param("id", maria.getId().toString()).param("version", "0")
				.param("lastName", "Αλεξίου").param("entityType", "INDIVIDUAL").param("mobile", "6900000001"),
				"Αποθηκεύτηκε.");
	}

	@Test
	void savesANewVehicle() throws Exception {
		landsWith(vehicleForm(post("/vehicles"), "ΚΜΝ-4321", "WVWZZZ1KZAW654321"), "Αποθηκεύτηκε.");
	}

	@Test
	void savesAnEditedVehicle() throws Exception {
		landsWith(vehicleForm(post("/vehicles/{id}", vehicle.getId()), "ΑΒΕ-1234", "WVWZZZ1KZAW123456")
				.param("id", vehicle.getId().toString()).param("version", "0"), "Αποθηκεύτηκε.");
	}

	@Test
	void savesTheOwners() throws Exception {
		landsWith(post("/vehicles/{id}/owners", vehicle.getId()).with(csrf())
				.param("vehicleVersion", "0").param("transferDate", TODAY.toString())
				.param("customerId", maria.getId().toString()).param("percentage", "100")
				.param("primary", maria.getId().toString()).param("action", "save"),
				"Αποθηκεύτηκε.");
	}

	@Test
	void savesANewPolicy() throws Exception {
		Vehicle uninsured = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		owns(uninsured, maria, null);

		landsWith(policyForm(post("/vehicles/{id}/policies", uninsured.getId()), uninsured, "2100000002",
				TODAY.minusMonths(1), TODAY.plusMonths(11)), "Αποθηκεύτηκε.");
	}

	@Test
	void savesAnEditedPolicy() throws Exception {
		landsWith(policyForm(post("/policies/{id}", policy.getId()), vehicle, "2100000001",
				policy.getStartDate(), policy.getEndDate())
				.param("id", policy.getId().toString()).param("version", "0"), "Αποθηκεύτηκε.");
	}

	// Task 12: saved as a new policy, but what the clerk did was renew.
	@Test
	void saysThePolicyWasRenewed() throws Exception {
		landsWith(policyForm(post("/vehicles/{id}/policies", vehicle.getId()), vehicle, "2100000002",
				policy.getEndDate(), policy.getEndDate().plusYears(1))
				.param("renewalOf", "2100000001"), "Το συμβόλαιο ανανεώθηκε.");
	}

	// The deletions keep what they said before: what went with it, and that
	// it can be put back from the log.
	@Test
	void saysACustomerWasDeleted() throws Exception {
		Customer nobody = customer("Γεωργίου", null);

		landsWith(post("/customers/{id}/delete", nobody.getId()).with(csrf()),
				"Ο πελάτης διαγράφηκε. Μπορεί να ανακτηθεί από το ιστορικό αλλαγών.");
	}

	@Test
	void saysAVehicleWasDeleted() throws Exception {
		landsWith(post("/vehicles/{id}/delete", vehicle.getId()).with(csrf()),
				"Το όχημα διαγράφηκε, μαζί με τα συμβόλαια και τις ιδιοκτησίες του.");
	}

	@Test
	void saysAPolicyWasDeleted() throws Exception {
		landsWith(post("/policies/{id}/delete", policy.getId()).with(csrf()),
				"Το συμβόλαιο διαγράφηκε. Μπορεί να ανακτηθεί από το ιστορικό αλλαγών.");
	}

	@Test
	void saysAFormerOwnershipWasDeleted() throws Exception {
		Ownership former = owns(vehicle, customer("Γεωργίου", null), TODAY.minusYears(1));

		landsWith(post("/ownerships/{id}/delete", former.getId()).with(csrf()), "Η παλιά ιδιοκτησία διαγράφηκε.");
	}

	// A form that comes back with a mistake has saved nothing, and says
	// nothing of the kind.
	@Test
	void saysNothingWhenTheSaveIsRefused() throws Exception {
		String html = mockMvc.perform(post("/customers").with(csrf()).param("entityType", "INDIVIDUAL"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(html).doesNotContain("class=\"container flash\"", "Αποθηκεύτηκε.");
	}

	// The shortcut and the print theme; loaded by the login page too.
	@Test
	@WithAnonymousUser
	void servesTheScriptEveryPageLoadsBeforeLogin() throws Exception {
		mockMvc.perform(get("/js/app.js")).andExpect(status().isOk());
		String login = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
		assertThat(login.indexOf("<script defer src=\"/js/app.js\"></script>")).isPositive()
				.isLessThan(login.indexOf("</head>"));
	}

	/**
	 * Performs the action, follows its redirect as the browser would, with
	 * the flash attributes, and checks the message is there, under the
	 * header, with a × to close it; then reloads the page, without them, and
	 * checks it is gone.
	 */
	private String landsWith(MockHttpServletRequestBuilder action, String message) throws Exception {
		MvcResult result = mockMvc.perform(action).andExpect(status().is3xxRedirection()).andReturn();
		String landing = result.getResponse().getRedirectedUrl();
		assertThat(landing).as("where the action lands").isNotNull();

		String html = mockMvc.perform(get(landing).flashAttrs(result.getFlashMap()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(flash(html)).contains(message, "alert-dismissible", "data-bs-dismiss=\"alert\"");
		assertThat(html.indexOf("class=\"container flash\"")).isGreaterThan(html.indexOf("</nav>"));

		String reloaded = mockMvc.perform(get(landing)).andReturn().getResponse().getContentAsString();
		assertThat(reloaded).doesNotContain("class=\"container flash\"");
		return html;
	}

	private static String flash(String html) {
		int start = html.indexOf("class=\"container flash\"");
		assertThat(start).as("the message block").isPositive();
		return html.substring(start, html.indexOf("<main", start));
	}

	private static MockHttpServletRequestBuilder vehicleForm(MockHttpServletRequestBuilder request, String plate,
			String vin) {
		return request.with(csrf())
				.param("plate", plate).param("vin", vin).param("brand", "Volkswagen").param("model", "Golf")
				.param("firstRegistration", "2012-05-14").param("category", "M1").param("usageType", "ΕΙΧ")
				.param("color", "Λευκό").param("engineCc", "1598").param("powerKw", "81")
				.param("fuelType", "ΒΕΝΖΙΝΗ");
	}

	private static MockHttpServletRequestBuilder policyForm(MockHttpServletRequestBuilder request, Vehicle vehicle,
			String policyNumber, LocalDate start, LocalDate end) {
		return request.with(csrf())
				.param("vehicleId", vehicle.getId().toString()).param("policyNumber", policyNumber)
				.param("insuranceCompany", "Northwind")
				.param("startDate", start.toString()).param("endDate", end.toString()).param("premium", "180,50");
	}

	private Customer customer(String lastName, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setMobile(mobile);
		return customerRepository.save(customer);
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
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private Ownership owns(Vehicle vehicle, Customer customer, LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownership.setToDate(toDate);
		return ownershipRepository.save(ownership);
	}

	private Policy policy(Vehicle vehicle, String policyNumber, LocalDate start, LocalDate end) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(start);
		policy.setEndDate(end);
		policy.setPremium(new BigDecimal("180.00"));
		return policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
