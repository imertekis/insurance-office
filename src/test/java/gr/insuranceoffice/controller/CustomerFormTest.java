package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

/** Task 11a: creating and editing a customer from the UI. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class CustomerFormTest {

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
	void opensAnEmptyFormFromTheNavigation() throws Exception {
		String html = html(get("/customers/new"));

		assertThat(html).contains("Νέος πελάτης", "Επώνυμο", "ΑΦΜ", "Κινητό", "Αποθήκευση",
				"action=\"/customers\"");
		// The header offers the form from every page.
		assertThat(html(get("/"))).contains("href=\"/customers/new\"");
	}

	@Test
	void createsACustomerAndLandsOnTheCard() throws Exception {
		mockMvc.perform(form("/customers")
				.param("lastName", "Αλεξίου").param("firstName", "Μαρία")
				.param("entityType", "INDIVIDUAL").param("taxId", "900000080")
				.param("mobile", "6900000001").param("city", "Δοκιμούπολη")
				.param("birthDate", "1980-04-15"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("warnings", List.of()));

		Customer stored = customerRepository.findByTaxId("900000080").orElseThrow();
		assertThat(stored.getLastName()).isEqualTo("Αλεξίου");
		assertThat(stored.getMobile()).isEqualTo("6900000001");
		assertThat(stored.getCity()).isEqualTo("Δοκιμούπολη");
		assertThat(stored.getBirthDate()).isEqualTo(LocalDate.of(1980, 4, 15));
		assertThat(stored.getVersion()).isZero();
	}

	// DECISIONS §2: saving without ΑΦΜ is allowed, with a warning on the card.
	@Test
	void savesWithoutATaxIdAndWarnsOnTheCard() throws Exception {
		MvcResult result = mockMvc.perform(form("/customers")
				.param("lastName", "Αλεξίου").param("entityType", "INDIVIDUAL").param("taxId", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("warnings", List.of("Λείπει το ΑΦΜ του πελάτη.")))
				.andReturn();

		assertThat(customerRepository.count()).isEqualTo(1);
		// The card shows it once, after the redirect.
		String card = mockMvc.perform(get(result.getResponse().getRedirectedUrl())
				.flashAttrs(result.getFlashMap()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(card).contains("Λείπει το ΑΦΜ του πελάτη.");
	}

	@Test
	void showsEveryInvalidFieldAndKeepsWhatWasTyped() throws Exception {
		String html = mockMvc.perform(form("/customers")
				.param("lastName", "Αλεξίου").param("entityType", "INDIVIDUAL")
				.param("taxId", "900000081").param("mobile", "2101234567").param("postalCode", "1234"))
				.andExpect(status().isOk())
				.andExpect(view().name("customer-form"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains(
				"Μη έγκυρο ΑΦΜ: 9 ψηφία με σωστό ψηφίο ελέγχου.",
				"Το κινητό πρέπει να έχει 10 ψηφία και να αρχίζει από 6.",
				"Ο Τ.Κ. πρέπει να έχει 5 ψηφία.",
				// Nothing is retyped: the values are still in the form.
				"value=\"900000081\"", "value=\"2101234567\"", "value=\"1234\"", "value=\"Αλεξίου\"");
		assertThat(customerRepository.count()).isZero();
	}

	@Test
	void editsAnExistingCustomer() throws Exception {
		Customer stored = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");

		// Editing starts from the card (Task 9).
		assertThat(html(get("/customers/{id}", stored.getId())))
				.contains("href=\"/customers/" + stored.getId() + "/edit\"", "Επεξεργασία");

		String form = html(get("/customers/{id}/edit", stored.getId()));
		assertThat(form).contains("Επεξεργασία πελάτη", "value=\"Αλεξίου\"", "value=\"900000080\"",
				"action=\"/customers/" + stored.getId() + "\"", "name=\"version\" value=\"0\"");

		mockMvc.perform(form("/customers/" + stored.getId())
				.param("id", stored.getId().toString()).param("version", "0")
				.param("lastName", "Αλεξίου").param("firstName", "Μαρία")
				.param("entityType", "INDIVIDUAL").param("taxId", "900000080")
				.param("mobile", "6900000002"))
				.andExpect(redirectedUrl("/customers/" + stored.getId()));

		assertThat(customerRepository.findById(stored.getId())).get()
				.extracting(Customer::getMobile, Customer::getVersion)
				.containsExactly("6900000002", 1L);
	}

	// SPEC §9: the second writer is told, and nothing is overwritten.
	@Test
	void refusesToSaveOverSomeoneElsesChange() throws Exception {
		Customer stored = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		stored.setCity("Δοκιμούπολη");
		customerRepository.save(stored);

		String html = mockMvc.perform(form("/customers/" + stored.getId())
				.param("id", stored.getId().toString()).param("version", "0")
				.param("lastName", "Αλεξίου").param("entityType", "INDIVIDUAL")
				.param("taxId", "900000080").param("mobile", "6900000003"))
				.andExpect(status().isOk())
				.andExpect(view().name("customer-form"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("άλλαξε από άλλον χρήστη", "value=\"6900000003\"");
		assertThat(customerRepository.findById(stored.getId())).get().extracting(Customer::getMobile)
				.isEqualTo("6900000001");
	}

	// DECISIONS §1: the office must be able to reach the primary owner of an
	// insured vehicle.
	@Test
	void refusesToClearTheMobileOfAPrimaryOwnerWithACurrentPolicy() throws Exception {
		Customer owner = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		Vehicle vehicle = vehicle();
		owns(vehicle, owner);
		policy(vehicle);

		String html = mockMvc.perform(form("/customers/" + owner.getId())
				.param("id", owner.getId().toString()).param("version", "0")
				.param("lastName", "Αλεξίου").param("entityType", "INDIVIDUAL")
				.param("taxId", "900000080").param("mobile", ""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("Το κινητό είναι υποχρεωτικό για τον κύριο ιδιοκτήτη");
		assertThat(customerRepository.findById(owner.getId())).get().extracting(Customer::getMobile)
				.isEqualTo("6900000001");
	}

	@Test
	void answers404ForACustomerThatNoLongerExists() throws Exception {
		mockMvc.perform(get("/customers/{id}/edit", 999)).andExpect(status().isNotFound());
	}

	// Task 11a replaces the temporary JSON endpoints of Tasks 1 and 7.
	@Test
	void noLongerAcceptsTheJsonApi() throws Exception {
		mockMvc.perform(post("/api/customers").with(csrf()).contentType("application/json").content("{}"))
				.andExpect(status().isNotFound());
	}

	private static MockHttpServletRequestBuilder form(String url) {
		return post(url).with(csrf());
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer(String lastName, String firstName, String taxId, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		customer.setMobile(mobile);
		return customerRepository.save(customer);
	}

	private Vehicle vehicle() {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW123456");
		vehicle.setPlate("ΑΒΕ-1234");
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

	private void owns(Vehicle vehicle, Customer customer) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownershipRepository.save(ownership);
	}

	private void policy(Vehicle vehicle) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("2100000001");
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(LocalDate.now().minusMonths(6));
		policy.setEndDate(LocalDate.now().plusMonths(6));
		policy.setPremium(new BigDecimal("180.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
