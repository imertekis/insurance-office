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
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/** The vehicle card (SPEC §7.2). */
@SpringBootTest
@AutoConfigureMockMvc
// Every page needs a logged-in user from Task 10 on; the login itself is in
// LoginTest.
@WithMockUser
@Import(TestcontainersConfiguration.class)
class VehicleControllerTest {

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
	private IntermediaryRepository intermediaryRepository;

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
	void showsTheLicenceFieldsWithTheirCodes() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");

		mockMvc.perform(get("/vehicles/{id}", vehicle.getId()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(view().name("vehicle-detail"));

		assertThat(html(get("/vehicles/{id}", vehicle.getId()))).contains(
				"Αρ. Κυκλοφορίας (A)", "ΑΒΕ1234",
				"Αρ. Πλαισίου / VIN (E)", "WVWZZZ1KZAW123456",
				"Μάρκα (D.1)", "Volkswagen", "Μοντέλο (D.3)", "Golf",
				"1η Άδεια (B)", "14/05/2012",
				"Κατηγορία (J)", "M1", "Χρήση Οχήματος", "ΕΙΧ", "Χρώμα (R)", "Λευκό",
				"Κυβικά (P.1)", "1598", "Ισχύς kW (P.2)", "81,00", "Καύσιμο (P.3)", "ΒΕΝΖΙΝΗ",
				"Αρ. Κινητήρα (P.5)", "ENG0001", "CO2 (V.7)", "120", "Euro (V.9)", "Euro 6",
				"Βάρος kg (G)", "1250");
	}

	// DATA_MODEL: an electric vehicle has no engine capacity.
	@Test
	void leavesTheEngineCapacityEmptyForAnElectricVehicle() throws Exception {
		Vehicle tesla = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		tesla.setEngineCc(null);
		tesla.setFuelType(FuelType.ΗΛΕΚΤΡΙΣΜΟΣ);
		vehicleRepository.save(tesla);

		assertThat(html(get("/vehicles/{id}", tesla.getId())))
				.contains("ΗΛΕΚΤΡΙΣΜΟΣ")
				.doesNotContain("1598");
	}

	@Test
	void showsOwnersWithTheirSharesAsLinks() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		Customer primary = customer("Αλεξίου", "Μαρία", "900000080");
		Customer coOwner = customer("Βασιλείου", "Νίκος", "900000091");
		Customer former = customer("Γεωργίου", "Άννα", null);
		owns(vehicle, primary, "60", true, null);
		owns(vehicle, coOwner, "40", false, null);
		owns(vehicle, former, "100", true, TODAY.minusYears(1));

		String html = html(get("/vehicles/{id}", vehicle.getId()));

		assertThat(html).contains(
				"href=\"/customers/" + primary.getId() + "\"", "Αλεξίου Μαρία", "60%", "Κύριος ιδιοκτήτης",
				"href=\"/customers/" + coOwner.getId() + "\"", "Βασιλείου Νίκος", "40%",
				"href=\"/customers/" + former.getId() + "\"", "Πρώην");
		// Current owners first, the former one last.
		assertThat(html.indexOf("Αλεξίου")).isLessThan(html.indexOf("Γεωργίου"));
	}

	@Test
	void showsTheCurrentPolicyAboveTheHistory() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		Intermediary intermediary = new Intermediary();
		intermediary.setFullName("Παπαδόπουλος Νίκος");
		intermediaryRepository.save(intermediary);
		policy(vehicle, "2100000001", TODAY.minusYears(2), TODAY.minusYears(1), null);
		policy(vehicle, "2100000002", TODAY.minusMonths(6), TODAY.plusMonths(6), intermediary);

		String html = html(get("/vehicles/{id}", vehicle.getId()));

		// The intermediary is on the policy form, no longer in this table (Task 13).
		assertThat(html).contains("2100000002", "Northwind", "Ενεργό", "180,00 €", "2100000001", "Ληγμένο")
				.doesNotContain("Παπαδόπουλος Νίκος", "Διαμεσολαβών");
		// Newest first.
		assertThat(html.indexOf("2100000002")).isLessThan(html.indexOf("2100000001"));
	}

	@Test
	void showsWhoHeldTheVehicleWhenEachPolicyStarted() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456");
		Customer seller = customer("Αλεξίου", "Μαρία", "900000080");
		Customer buyer = customer("Βασιλείου", "Νίκος", "900000091");
		LocalDate transfer = TODAY.minusMonths(8);
		owns(vehicle, seller, "100", true, null, transfer);
		owns(vehicle, buyer, "100", true, transfer, null);
		policy(vehicle, "2100000001", TODAY.minusYears(2), TODAY.minusYears(1), null);
		policy(vehicle, "2100000002", TODAY.minusMonths(6), TODAY.plusMonths(6), null);

		String html = html(get("/vehicles/{id}", vehicle.getId()));
		String older = row(html, "2100000001");
		String newer = row(html, "2100000002");

		assertThat(older).contains("href=\"/customers/" + seller.getId() + "\"", "Αλεξίου Μαρία")
				.doesNotContain("Βασιλείου");
		assertThat(newer).contains("href=\"/customers/" + buyer.getId() + "\"", "Βασιλείου Νίκος")
				.doesNotContain("Αλεξίου");
	}

	@Test
	void givesAPolicyStartingOnTheTransferDayToTheNewOwner() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456");
		Customer seller = customer("Αλεξίου", "Μαρία", "900000080");
		Customer buyer = customer("Βασιλείου", "Νίκος", "900000091");
		LocalDate transfer = TODAY.minusMonths(3);
		owns(vehicle, seller, "100", true, null, transfer);
		owns(vehicle, buyer, "100", true, transfer, null);
		policy(vehicle, "2100000001", transfer, transfer.plusYears(1), null);

		assertThat(row(html(get("/vehicles/{id}", vehicle.getId())), "2100000001"))
				.contains("Βασιλείου Νίκος").doesNotContain("Αλεξίου");
	}

	@Test
	void namesTheCurrentPrimaryOwnerWhenTheOwnershipHasNoDates() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456");
		Customer primary = customer("Αλεξίου", "Μαρία", "900000080");
		Customer coOwner = customer("Βασιλείου", "Νίκος", "900000091");
		owns(vehicle, primary, "60", true, null);
		owns(vehicle, coOwner, "40", false, null);
		policy(vehicle, "2100000001", TODAY.minusYears(3), TODAY.minusYears(2), null);

		assertThat(row(html(get("/vehicles/{id}", vehicle.getId())), "2100000001"))
				.contains("href=\"/customers/" + primary.getId() + "\"", "Αλεξίου Μαρία")
				.doesNotContain("Βασιλείου");
	}

	@Test
	void namesTheCurrentPrimaryOwnerWhenNoOwnershipCoversTheStart() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456");
		Customer owner = customer("Αλεξίου", "Μαρία", "900000080");
		// The first owner on record started after the policy did.
		owns(vehicle, owner, "100", true, TODAY.minusMonths(1), null);
		policy(vehicle, "2100000001", TODAY.minusYears(3), TODAY.minusYears(2), null);

		assertThat(row(html(get("/vehicles/{id}", vehicle.getId())), "2100000001")).contains("Αλεξίου Μαρία");
	}

	@Test
	void showsADashWhenTheVehicleHasNoPrimaryOwner() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456");
		policy(vehicle, "2100000001", TODAY.minusMonths(6), TODAY.plusMonths(6), null);

		assertThat(row(html(get("/vehicles/{id}", vehicle.getId())), "2100000001")).doesNotContain("/customers/");
	}

	@Test
	void marksAPolicyThatExpiresSoon() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		policy(vehicle, "2100000001", TODAY.minusMonths(6), TODAY.plusDays(10), null);

		assertThat(html(get("/vehicles/{id}", vehicle.getId()))).contains("Λήγει σύντομα");
	}

	@Test
	void saysSoWhenTheVehicleHasNoOwnersOrPolicies() throws Exception {
		Vehicle vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");

		assertThat(html(get("/vehicles/{id}", vehicle.getId())))
				.contains("Το όχημα δεν έχει ιδιοκτήτες", "Το όχημα δεν έχει συμβόλαια");
	}

	@Test
	void answers404ForAVehicleThatDoesNotExist() throws Exception {
		mockMvc.perform(get("/vehicles/{id}", 999))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Το όχημα δεν βρέθηκε")));
	}

	private String html(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	// The table row of one policy, so a name elsewhere on the page does not count.
	private static String row(String html, String policyNumber) {
		int number = html.indexOf(policyNumber);
		assertThat(number).as("policy " + policyNumber).isPositive();
		return html.substring(html.lastIndexOf("<tr", number), html.indexOf("</tr>", number));
	}

	private Customer customer(String lastName, String firstName, String taxId) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		return customerRepository.save(customer);
	}

	private Vehicle vehicle(String plate, String vin) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setLicenseIssueDate(LocalDate.of(2021, 11, 22));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setSeats((short) 5);
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		vehicle.setEngineNumber("ENG0001");
		vehicle.setCo2(120);
		vehicle.setEmissionStandard("Euro 6");
		vehicle.setWeightKg(1250);
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

	private void policy(Vehicle vehicle, String policyNumber, LocalDate startDate, LocalDate endDate,
			Intermediary intermediary) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setIntermediary(intermediary);
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
