package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

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

/** The customer card (SPEC §7.3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerControllerTest {

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
	void showsTheCustomersDetails() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		customer.setFatherName("Γεώργιος");
		customer.setTaxOffice("Δοκιμούπολης");
		customer.setBirthDate(LocalDate.of(1980, 4, 15));
		customer.setLicenseDate(LocalDate.of(1999, 5, 20));
		customer.setStreet("Οδός Δοκιμής 1");
		customer.setCity("Δοκιμούπολη");
		customer.setPostalCode("99000");
		customer.setMobile("6900000001");
		customer.setPhone("2990000000");
		customer.setEmail("test@example.com");
		customerRepository.save(customer);

		mockMvc.perform(get("/customers/{id}", customer.getId()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(view().name("customer-detail"));

		assertThat(html(get("/customers/{id}", customer.getId()))).contains("Αλεξίου Μαρία", "Γεώργιος", "900000080",
				"Δοκιμούπολης", "15/04/1980", "20/05/1999", "Οδός Δοκιμής 1", "Δοκιμούπολη", "99000",
				"6900000001", "2990000000", "test@example.com");
	}

	// DECISIONS §1 and §2: both may be missing, and the card says so.
	@Test
	void flagsAMissingTaxIdAndMobile() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", null);

		assertThat(html(get("/customers/{id}", customer.getId()))).contains("Λείπει");
	}

	@Test
	void showsTheVehiclesOwnedWithTheirShareAsLinks() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		Vehicle clio = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321", "Clio");
		Vehicle sold = vehicle("ΖΗΡ-9999", "WVWZZZ1KZAW999999", "Corsa");
		owns(golf, customer, "60", true, null);
		owns(clio, customer, "100", true, null);
		owns(sold, customer, "100", true, TODAY.minusYears(1));

		String html = html(get("/customers/{id}", customer.getId()));

		assertThat(html).contains(
				"href=\"/vehicles/" + golf.getId() + "\"", "ΑΒΕ-1234", "Volkswagen Golf", "60%",
				"href=\"/vehicles/" + clio.getId() + "\"", "ΚΜΝ-4321", "100%",
				"href=\"/vehicles/" + sold.getId() + "\"", "Πρώην");
		// Vehicles still owned come first.
		assertThat(html.indexOf("ΑΒΕ-1234")).isLessThan(html.indexOf("ΖΗΡ-9999"));
	}

	// SPEC §7.3: the policies of all their vehicles together, with a status.
	@Test
	void showsThePoliciesOfEveryVehicleWithItsStatus() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		Vehicle clio = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321", "Clio");
		owns(golf, customer, "100", true, null);
		owns(clio, customer, "100", true, null);
		policy(golf, "2100000001", TODAY.minusYears(2), TODAY.minusYears(1));
		policy(golf, "2100000002", TODAY.minusMonths(6), TODAY.plusMonths(6));
		policy(clio, "2100000003", TODAY.minusMonths(11), TODAY.plusDays(10));

		String html = html(get("/customers/{id}", customer.getId()));

		assertThat(html).contains("2100000001", "Ληγμένο", "2100000002", "Ενεργό", "2100000003", "Λήγει σύντομα",
				"href=\"/vehicles/" + golf.getId() + "\"", "href=\"/vehicles/" + clio.getId() + "\"");
		// Newest first.
		assertThat(html.indexOf("2100000002")).isLessThan(html.indexOf("2100000001"));
	}

	@Test
	void saysSoWhenTheCustomerHasNoVehiclesOrPolicies() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");

		assertThat(html(get("/customers/{id}", customer.getId())))
				.contains("Ο πελάτης δεν έχει οχήματα", "Ο πελάτης δεν έχει συμβόλαια");
	}

	@Test
	void answers404ForACustomerThatDoesNotExist() throws Exception {
		mockMvc.perform(get("/customers/{id}", 999))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Ο πελάτης δεν βρέθηκε")));
	}

	private String html(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer(String lastName, String firstName, String taxId) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		return customerRepository.save(customer);
	}

	private Vehicle vehicle(String plate, String vin, String model) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel(model);
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private void owns(Vehicle vehicle, Customer customer, String percentage, boolean primary, LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal(percentage));
		ownership.setPrimary(primary);
		ownership.setToDate(toDate);
		ownershipRepository.save(ownership);
	}

	private void policy(Vehicle vehicle, String policyNumber, LocalDate startDate, LocalDate endDate) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(startDate);
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal("180.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
