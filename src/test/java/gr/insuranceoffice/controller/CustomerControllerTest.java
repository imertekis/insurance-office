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
import org.springframework.security.test.context.support.WithMockUser;
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
// Every page needs a logged-in user from Task 10 on; the login itself is in
// LoginTest.
@WithMockUser
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
				"href=\"/vehicles/" + golf.getId() + "\"", "ΑΒΕ1234", "Volkswagen Golf", "60%",
				"href=\"/vehicles/" + clio.getId() + "\"", "ΚΜΝ4321", "100%",
				"href=\"/vehicles/" + sold.getId() + "\"", "Πρώην");
		// Vehicles still owned come first.
		assertThat(html.indexOf("ΑΒΕ1234")).isLessThan(html.indexOf("ΖΗΡ9999"));
	}

	// Task 16d-2: the mobile calls from a phone.
	@Test
	void linksTheMobileForCalling() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		customer.setMobile("6900000001");
		customerRepository.save(customer);

		assertThat(html(get("/customers/{id}", customer.getId())))
				.contains("<a href=\"tel:6900000001\">6900000001</a>");
	}

	// Task 16d-2: ownership brought in by the import has no dates.
	@Test
	void hidesTheFromToColumnWhenNoVehicleHasADate() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		owns(vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf"), customer, "100", true, null);
		owns(vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321", "Clio"), customer, "50", false, null);

		assertThat(table(html(get("/customers/{id}", customer.getId())), "vehicles"))
				.contains("ΑΒΕ1234", "ΚΜΝ4321")
				.doesNotContain("Από / Έως", "— / —");
	}

	@Test
	void showsTheFromToColumnWhenOneVehicleHasADate() throws Exception {
		Customer customer = customer("Αλεξίου", "Μαρία", "900000080");
		owns(vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf"), customer, "100", true, null);
		owns(vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321", "Clio"), customer, "100", true, LocalDate.of(2025, 2, 20),
				null);

		assertThat(table(html(get("/customers/{id}", customer.getId())), "vehicles"))
				.contains("Από / Έως", "— / —", "20/02/2025 / —");
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

	// Only the policies that started while the customer owned the vehicle, by
	// the rule of Task 13: a buyer's policies are not the seller's.
	@Test
	void doesNotShowTheFormerOwnerThePoliciesTheBuyerTookOut() throws Exception {
		Customer seller = customer("Αλεξίου", "Μαρία", "900000080");
		Customer buyer = customer("Βασιλείου", "Νίκος", "900000091");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		LocalDate transfer = TODAY.minusMonths(8);
		owns(golf, seller, "100", true, null, transfer);
		owns(golf, buyer, "100", true, transfer, null);
		policy(golf, "2100000001", TODAY.minusYears(2), TODAY.minusYears(1));
		policy(golf, "2100000002", TODAY.minusMonths(6), TODAY.plusMonths(6));

		String sellersCard = html(get("/customers/{id}", seller.getId()));
		String buyersCard = html(get("/customers/{id}", buyer.getId()));

		// The seller still has the vehicle on the card, as a former one, and
		// the policy of their own time; not the buyer's.
		assertThat(sellersCard).contains("href=\"/vehicles/" + golf.getId() + "\"", "Πρώην", "2100000001")
				.doesNotContain("2100000002");
		// Nor does the buyer get the policies from before they owned it.
		assertThat(buyersCard).contains("2100000002").doesNotContain("2100000001");
	}

	// A transfer date closes the seller's ownership and opens the buyer's, so
	// the day itself is the buyer's.
	@Test
	void givesAPolicyStartingOnTheTransferDayToTheBuyer() throws Exception {
		Customer seller = customer("Αλεξίου", "Μαρία", "900000080");
		Customer buyer = customer("Βασιλείου", "Νίκος", "900000091");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		LocalDate transfer = TODAY.minusMonths(3);
		owns(golf, seller, "100", true, null, transfer);
		owns(golf, buyer, "100", true, transfer, null);
		policy(golf, "2100000001", transfer.minusDays(1), transfer.plusMonths(6));
		policy(golf, "2100000002", transfer, transfer.plusYears(1));

		String sellersCard = html(get("/customers/{id}", seller.getId()));
		String buyersCard = html(get("/customers/{id}", buyer.getId()));

		assertThat(sellersCard).contains("2100000001").doesNotContain("2100000002");
		assertThat(buyersCard).contains("2100000002").doesNotContain("2100000001");
	}

	@Test
	void showsACoOwnerThePoliciesOfTheVehicle() throws Exception {
		Customer primary = customer("Αλεξίου", "Μαρία", "900000080");
		Customer coOwner = customer("Βασιλείου", "Νίκος", "900000091");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		owns(golf, primary, "60", true, null, null);
		owns(golf, coOwner, "40", false, TODAY.minusYears(3), null);
		policy(golf, "2100000001", TODAY.minusYears(2), TODAY.minusYears(1));
		policy(golf, "2100000002", TODAY.minusMonths(6), TODAY.plusMonths(6));

		assertThat(html(get("/customers/{id}", coOwner.getId()))).contains("2100000001", "2100000002");
		assertThat(html(get("/customers/{id}", primary.getId()))).contains("2100000001", "2100000002");
	}

	// Sold and bought back: the policies of both of their times, not the
	// one in between.
	@Test
	void showsAnOwnerWhoBoughtTheVehicleBackThePoliciesOfBothTheirTimes() throws Exception {
		Customer first = customer("Αλεξίου", "Μαρία", "900000080");
		Customer other = customer("Βασιλείου", "Νίκος", "900000091");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456", "Golf");
		LocalDate sold = TODAY.minusMonths(18);
		LocalDate boughtBack = TODAY.minusMonths(6);
		owns(golf, first, "100", true, null, sold);
		owns(golf, other, "100", true, sold, boughtBack);
		owns(golf, first, "100", true, boughtBack, null);
		policy(golf, "2100000001", sold.minusMonths(3), sold.plusMonths(9));
		policy(golf, "2100000002", sold.plusMonths(1), sold.plusMonths(13));
		policy(golf, "2100000003", boughtBack.plusMonths(1), boughtBack.plusMonths(13));

		assertThat(html(get("/customers/{id}", first.getId()))).contains("2100000001", "2100000003")
				.doesNotContain("2100000002");
		assertThat(html(get("/customers/{id}", other.getId()))).contains("2100000002")
				.doesNotContain("2100000001", "2100000003");
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

	// One table of the page, so a heading elsewhere does not count.
	private static String table(String html, String id) {
		int start = html.indexOf("<table class=\"table table-hover align-middle\" id=\"" + id + "\"");
		assertThat(start).as("table " + id).isPositive();
		return html.substring(start, html.indexOf("</table>", start));
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
		owns(vehicle, customer, percentage, primary, null, toDate);
	}

	private void owns(Vehicle vehicle, Customer customer, String percentage, boolean primary, LocalDate fromDate,
			LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal(percentage));
		ownership.setPrimary(primary);
		ownership.setFromDate(fromDate);
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
