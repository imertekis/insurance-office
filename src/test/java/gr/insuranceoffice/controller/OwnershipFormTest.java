package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Task 11c: all current owners of a vehicle, edited together. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class OwnershipFormTest {

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

	private Customer nikos;

	private Customer anna;

	private Vehicle vehicle;

	@BeforeEach
	void startWithOneOwner() {
		truncateTables();
		maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		// DECISIONS §1: allowed, until she would answer for an insured vehicle.
		anna = customer("Γεωργίου", "Άννα", "123456783", null);
		vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		// As the import leaves it: no transfer dates.
		owns(vehicle, maria, "100", true, null, null);
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void showsTheCurrentOwnersButNotTheFormerOnes() throws Exception {
		owns(vehicle, anna, "100", true, LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1));

		String html = html(get("/vehicles/{id}/owners", vehicle.getId()));

		assertThat(html).contains("ΑΒΕ1234", "Αλεξίου Μαρία", "name=\"percentage\"", "value=\"100\"",
				"value=\"" + TODAY + "\"")
				.doesNotContain("Γεωργίου Άννα");
		assertThat(html).containsPattern("value=\"" + maria.getId() + "\"[^>]*checked");
		// Task 16d-1: the date field shows what to type.
		assertThat(html).containsPattern("id=\"transferDate\"[^>]*placeholder=\"ηη/μμ/εεεε\"");
		// Reached from the vehicle card (Task 9).
		assertThat(html(get("/vehicles/{id}", vehicle.getId())))
				.contains("href=\"/vehicles/" + vehicle.getId() + "/owners\"");
	}

	@Test
	void findsACustomerAndAddsARowWithoutSavingIt() throws Exception {
		String found = html(owners(List.of(maria), List.of("100"), maria)
				.param("action", "search").param("q", "900000091"));
		assertThat(found).contains("Βασιλείου Νίκος", "name=\"add\" value=\"" + nikos.getId() + "\"")
				// Already an owner, so not offered again.
				.doesNotContain("name=\"add\" value=\"" + maria.getId() + "\"");

		String added = html(owners(List.of(maria), List.of("100"), maria).param("add", nikos.getId().toString()));
		assertThat(added).contains("Αλεξίου Μαρία", "Βασιλείου Νίκος");

		// Nothing is written until "Αποθήκευση".
		assertThat(ownershipRepository.count()).isEqualTo(1);
	}

	// A vehicle's first owner owns all of it until told otherwise.
	@Test
	void makesTheFirstOwnerOfAnEmptyVehiclePrimaryWithAllOfIt() throws Exception {
		Vehicle empty = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");

		String html = html(post("/vehicles/{id}/owners", empty.getId()).with(csrf())
				.param("vehicleVersion", "0").param("transferDate", TODAY.toString())
				.param("add", nikos.getId().toString()));

		assertThat(html).contains("Βασιλείου Νίκος", "value=\"100\"");
		assertThat(html).containsPattern("value=\"" + nikos.getId() + "\"[^>]*checked");
	}

	@Test
	void savesASplitAndLandsOnTheCard() throws Exception {
		mockMvc.perform(owners(List.of(maria, nikos), List.of("60", "40"), maria).param("action", "save"))
				.andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

		List<Ownership> current = current();
		assertThat(current).extracting(o -> o.getCustomer().getId(), Ownership::getPercentage, Ownership::isPrimary,
				Ownership::getFromDate)
				.containsExactlyInAnyOrder(
						// Maria stays on her own row, with the new share.
						tuple(maria.getId(), new BigDecimal("60.00"), true, null),
						// Nikos joins on the transfer date.
						tuple(nikos.getId(), new BigDecimal("40.00"), false, TODAY));
		assertThat(ownershipRepository.count()).isEqualTo(2);
	}

	// Task 11c decision: a transfer is an event with a date, so the row stays.
	@Test
	void closesARemovedOwnerOnTheTransferDateInsteadOfDeletingIt() throws Exception {
		owns(vehicle, nikos, "40", false, null, null);
		jdbcTemplate.update("UPDATE ownership SET percentage = 60 WHERE customer_id = ?", maria.getId());
		LocalDate sold = TODAY.minusDays(3);

		mockMvc.perform(owners(List.of(maria), List.of("100"), maria, sold).param("action", "save"))
				.andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

		assertThat(current()).extracting(o -> o.getCustomer().getId()).containsExactly(maria.getId());
		assertThat(ownershipRepository.count()).isEqualTo(2);
		assertThat(ownershipRepository.findAll()).filteredOn(o -> o.getCustomer().getId().equals(nikos.getId()))
				.singleElement().extracting(Ownership::getToDate).isEqualTo(sold);
		// The card shows him as a former owner.
		assertThat(html(get("/vehicles/{id}", vehicle.getId()))).contains("Βασιλείου Νίκος", "Πρώην");
	}

	@Test
	void refusesSharesThatDoNotAddUpAndKeepsWhatWasTyped() throws Exception {
		String html = html(owners(List.of(maria, nikos), List.of("60", "30"), maria).param("action", "save"));

		assertThat(html).contains("Τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν 90% αντί για 100%.",
				"value=\"60\"", "value=\"30\"");
		assertThat(current()).singleElement().extracting(Ownership::getPercentage)
				.isEqualTo(new BigDecimal("100.00"));
	}

	@Test
	void requiresAPrimaryOwner() throws Exception {
		String html = html(owners(List.of(maria, nikos), List.of("50", "50"), null).param("action", "save"));

		assertThat(html).contains("Το όχημα πρέπει να έχει ακριβώς έναν κύριο ιδιοκτήτη, έχει 0.");
		assertThat(ownershipRepository.count()).isEqualTo(1);
	}

	@Test
	void acceptsTheGreekDecimalComma() throws Exception {
		mockMvc.perform(owners(List.of(maria, nikos), List.of("66,67", "33,33"), maria).param("action", "save"))
				.andExpect(status().is3xxRedirection());

		assertThat(current()).extracting(Ownership::getPercentage)
				.containsExactlyInAnyOrder(new BigDecimal("66.67"), new BigDecimal("33.33"));
	}

	// A single value with a comma must not be split into two rows.
	@Test
	void readsASingleShareWithACommaAsOneValue() throws Exception {
		mockMvc.perform(owners(List.of(maria), List.of("100,00"), maria).param("action", "save"))
				.andExpect(status().is3xxRedirection());

		assertThat(current()).singleElement().extracting(Ownership::getPercentage)
				.isEqualTo(new BigDecimal("100.00"));
	}

	@Test
	void showsAnUnreadableShareNextToItsRow() throws Exception {
		String html = html(owners(List.of(maria, nikos), List.of("πενήντα", "50"), maria).param("action", "save"));

		assertThat(html).contains("Συμπληρώστε αριθμό, π.χ. 50 ή 33,33.", "value=\"πενήντα\"");
		assertThat(ownershipRepository.count()).isEqualTo(1);
	}

	// DECISIONS §1, from the ownership side (NOTES "Mobile rule from the other side").
	@Test
	void refusesAPrimaryOwnerWithoutAMobileForAnInsuredVehicle() throws Exception {
		policy(vehicle);

		String html = html(owners(List.of(maria, anna), List.of("50", "50"), anna).param("action", "save"));

		assertThat(html).contains("Γεωργίου Άννα δεν έχει κινητό");
		assertThat(current()).singleElement().extracting(o -> o.getCustomer().getId()).isEqualTo(maria.getId());
	}

	@Test
	void allowsAPrimaryOwnerWithoutAMobileWhileTheVehicleIsUninsured() throws Exception {
		mockMvc.perform(owners(List.of(maria, anna), List.of("50", "50"), anna).param("action", "save"))
				.andExpect(status().is3xxRedirection());

		assertThat(current()).filteredOn(Ownership::isPrimary).singleElement()
				.extracting(o -> o.getCustomer().getId()).isEqualTo(anna.getId());
	}

	@Test
	void refusesATransferInTheFuture() throws Exception {
		String html = html(owners(List.of(maria, nikos), List.of("50", "50"), maria, TODAY.plusDays(1))
				.param("action", "save"));

		assertThat(html).contains("Η ημερομηνία μεταβίβασης δεν μπορεί να είναι μελλοντική.");
		assertThat(ownershipRepository.count()).isEqualTo(1);
	}

	// Ownership has no version of its own: saving it raises the vehicle's, so
	// two clerks cannot both win (SPEC §9).
	@Test
	void raisesTheVehicleVersionAndRefusesAnOutdatedForm() throws Exception {
		mockMvc.perform(owners(List.of(maria, nikos), List.of("60", "40"), maria).param("action", "save"))
				.andExpect(status().is3xxRedirection());
		assertThat(vehicleRepository.findById(vehicle.getId())).get().extracting(Vehicle::getVersion)
				.isEqualTo(1L);

		// A second clerk still holding version 0.
		String html = html(owners(List.of(maria), List.of("100"), maria).param("action", "save"));

		assertThat(html).contains("άλλαξαν από άλλον χρήστη");
		assertThat(current()).hasSize(2);
	}

	@Test
	void answers404ForAVehicleThatDoesNotExist() throws Exception {
		mockMvc.perform(get("/vehicles/{id}/owners", 999)).andExpect(status().isNotFound());
	}

	// The form as a browser sends it, with version 0 and today's transfer date.
	private MockHttpServletRequestBuilder owners(List<Customer> customers, List<String> percentages,
			Customer primary) {
		return owners(customers, percentages, primary, TODAY);
	}

	// param() adds a value rather than replacing it, so the date is chosen here.
	private MockHttpServletRequestBuilder owners(List<Customer> customers, List<String> percentages,
			Customer primary, LocalDate transferDate) {
		MockHttpServletRequestBuilder request = post("/vehicles/{id}/owners", vehicle.getId()).with(csrf())
				.param("vehicleVersion", "0").param("transferDate", transferDate.toString());
		customers.forEach(customer -> request.param("customerId", customer.getId().toString()));
		percentages.forEach(percentage -> request.param("percentage", percentage));
		if (primary != null) {
			request.param("primary", primary.getId().toString());
		}
		return request;
	}

	private List<Ownership> current() {
		return ownershipRepository.findCurrentByVehicleIdWithCustomer(vehicle.getId());
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

	private void policy(Vehicle vehicle) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("2100000001");
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(TODAY.minusMonths(6));
		policy.setEndDate(TODAY.plusMonths(6));
		policy.setPremium(new BigDecimal("180.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
