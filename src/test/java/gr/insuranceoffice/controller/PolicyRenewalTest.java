package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Task 12: renewing a policy, the office's most frequent action, from the
 * dashboard and the vehicle card, without an empty form. A clerk, not a
 * ΔΙΑΧΕΙΡΙΣΤΗΣ: renewing is everyday work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ΥΠΑΛΛΗΛΟΣ")
@Import(TestcontainersConfiguration.class)
class PolicyRenewalTest {

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

	private Vehicle vehicle;

	private Intermediary papadopoulos;

	private Policy older;

	private Policy current;

	@BeforeEach
	void aVehicleDueForRenewal() {
		truncateTables();
		vehicle = vehicle();
		owns(vehicle, customer());
		papadopoulos = intermediary();
		older = policy("2100000001", TODAY.minusMonths(18), TODAY.minusMonths(6), false, null);
		// Six months, ending in ten days, with a surcharge.
		current = policy("2100000002", TODAY.minusMonths(6), TODAY.plusDays(10), true, SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ);
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void offersRenewalOnEveryDashboardRow() throws Exception {
		Vehicle second = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		owns(second, customer());
		Policy other = policy(second, "2100000011", TODAY.minusYears(1), TODAY.plusDays(20), false, null);

		String dashboard = html(get("/"));

		assertThat(dashboard).contains("href=\"/policies/" + current.getId() + "/renew\"",
				"href=\"/policies/" + other.getId() + "/renew\"", "Ανανέωση");
	}

	// The expired view lists renewals still to do, so it offers them too.
	@Test
	void offersRenewalOnAnExpiredPolicyStillWaitingForIt() throws Exception {
		Vehicle lapsed = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		owns(lapsed, customer());
		Policy expired = policy(lapsed, "2100000011", TODAY.minusYears(1), TODAY.minusDays(5), false, null);

		assertThat(html(get("/").param("period", "expired")))
				.contains("href=\"/policies/" + expired.getId() + "/renew\"");
	}

	// TASKS Task 12: on the latest policy only, not on the history.
	@Test
	void offersRenewalOnTheVehicleCardsLatestPolicyOnly() throws Exception {
		String card = html(get("/vehicles/{id}", vehicle.getId()));

		assertThat(card).contains("href=\"/policies/" + current.getId() + "/renew\"")
				.doesNotContain("href=\"/policies/" + older.getId() + "/renew\"");
	}

	// On a renewal's first day both are in force; the newer one is renewed next.
	@Test
	void offersRenewalOnTheNewerPolicyOnTheBoundaryDay() throws Exception {
		Vehicle renewedToday = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		owns(renewedToday, customer());
		Policy ending = policy(renewedToday, "2100000011", TODAY.minusYears(1), TODAY, false, null);
		Policy starting = policy(renewedToday, "2100000012", TODAY, TODAY.plusYears(1), false, null);

		assertThat(html(get("/vehicles/{id}", renewedToday.getId())))
				.contains("href=\"/policies/" + starting.getId() + "/renew\"")
				.doesNotContain("href=\"/policies/" + ending.getId() + "/renew\"");
	}

	@Test
	void opensThePolicyFormPrefilledFromTheCurrentPolicy() throws Exception {
		String form = html(get("/policies/{id}/renew", current.getId()));

		assertThat(form).contains("Ανανέωση συμβολαίου", "ανανέωση του 2100000002", "ΑΒΕ-1234",
				// The Task 11d form, posting a new policy for the same vehicle.
				"action=\"/vehicles/" + vehicle.getId() + "/policies\"",
				"value=\"Northwind\"", "value=\"180,50\"",
				// Starts the day the current one ends: touching is not overlapping.
				"id=\"startDate\" name=\"startDate\" value=\"" + current.getEndDate() + "\"")
				.containsPattern("value=\"" + papadopoulos.getId() + "\"[^>]*selected")
				.containsPattern("id=\"surcharge\"[^>]*checked")
				.containsPattern("value=\"ΝΕΟΣ_ΟΔΗΓΟΣ\"[^>]*selected");
		// A new number, and no assumed duration: both left to the clerk.
		assertThat(form).contains("id=\"policyNumber\" name=\"policyNumber\" value=\"\"",
				"id=\"endDate\" name=\"endDate\" value=\"\"");
	}

	@Test
	void savesANewPolicyAndLeavesTheOldOneAsHistory() throws Exception {
		mockMvc.perform(renewal("2100000003", current.getEndDate().plusMonths(6), "195,00"))
				.andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

		Policy renewed = policyRepository.findByPolicyNumber("2100000003").orElseThrow();
		assertThat(renewed.getId()).isNotEqualTo(current.getId());
		assertThat(renewed.getStartDate()).isEqualTo(current.getEndDate());
		assertThat(renewed.getEndDate()).isEqualTo(current.getEndDate().plusMonths(6));
		assertThat(renewed.getPremium()).isEqualByComparingTo("195.00");
		assertThat(renewed.getInsuranceCompany()).isEqualTo("Northwind");
		assertThat(renewed.getSurchargeType()).isEqualTo(SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ);
		assertThat(jdbcTemplate.queryForObject("SELECT intermediary_id FROM policy WHERE id = ?", Long.class,
				renewed.getId())).isEqualTo(papadopoulos.getId());

		// The old policy is untouched.
		Policy old = policyRepository.findById(current.getId()).orElseThrow();
		assertThat(old.getPolicyNumber()).isEqualTo("2100000002");
		assertThat(old.getEndDate()).isEqualTo(TODAY.plusDays(10));
		assertThat(old.getPremium()).isEqualByComparingTo("180.50");
		assertThat(old.getVersion()).isZero();
		assertThat(policyRepository.count()).isEqualTo(3);

		// Renewed, so no longer work to do (SPEC §7.1); the card offers the
		// next renewal on the new policy.
		assertThat(html(get("/"))).doesNotContain("2100000002");
		assertThat(html(get("/vehicles/{id}", vehicle.getId())))
				.contains("href=\"/policies/" + renewed.getId() + "/renew\"")
				.doesNotContain("href=\"/policies/" + current.getId() + "/renew\"");
	}

	// Every Task 11d rule still applies, and the form stays a renewal.
	@Test
	void appliesThePolicyRulesAndStaysARenewal() throws Exception {
		assertThat(html(renewal("2100000002", current.getEndDate().plusMonths(6), "195,00")))
				.contains("Υπάρχει ήδη συμβόλαιο με αυτόν τον αριθμό.", "Ανανέωση συμβολαίου",
						"ανανέωση του 2100000002");
		assertThat(html(renewal("2100000003", current.getEndDate(), "195,00")))
				.contains("Η λήξη πρέπει να είναι μετά την έναρξη.");
		assertThat(policyRepository.count()).isEqualTo(2);
	}

	@Test
	void refusesARenewalThatOverlapsTheCurrentPolicy() throws Exception {
		String html = html(post("/vehicles/{id}/policies", vehicle.getId()).with(csrf())
				.param("renewalOf", "2100000002")
				.param("policyNumber", "2100000003").param("insuranceCompany", "Northwind")
				.param("startDate", TODAY.toString()).param("endDate", TODAY.plusMonths(6).toString())
				.param("premium", "195,00"));

		assertThat(html).contains("Επικαλύπτεται με το συμβόλαιο 2100000002");
		assertThat(policyRepository.count()).isEqualTo(2);
	}

	@Test
	void answers404ForAPolicyThatDoesNotExist() throws Exception {
		mockMvc.perform(get("/policies/{id}/renew", 999)).andExpect(status().isNotFound());
	}

	// The renewal form as the clerk submits it: the prefilled fields plus the
	// new number, end and premium.
	private MockHttpServletRequestBuilder renewal(String policyNumber, LocalDate endDate, String premium) {
		return post("/vehicles/{id}/policies", vehicle.getId()).with(csrf())
				.param("renewalOf", "2100000002").param("vehicleId", vehicle.getId().toString())
				.param("policyNumber", policyNumber).param("insuranceCompany", "Northwind")
				.param("intermediaryId", papadopoulos.getId().toString())
				.param("startDate", current.getEndDate().toString()).param("endDate", endDate.toString())
				.param("premium", premium).param("surcharge", "true").param("surchargeType", "ΝΕΟΣ_ΟΔΗΓΟΣ");
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer() {
		Customer customer = new Customer();
		customer.setLastName("Αλεξίου");
		customer.setFirstName("Μαρία");
		customer.setMobile("6900000001");
		return customerRepository.save(customer);
	}

	private Vehicle vehicle() {
		return vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
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

	private void owns(Vehicle vehicle, Customer customer) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownershipRepository.save(ownership);
	}

	private Intermediary intermediary() {
		Intermediary intermediary = new Intermediary();
		intermediary.setFullName("Παπαδόπουλος Νίκος");
		return intermediaryRepository.save(intermediary);
	}

	private Policy policy(String policyNumber, LocalDate startDate, LocalDate endDate, boolean surcharge,
			SurchargeType surchargeType) {
		return policy(vehicle, policyNumber, startDate, endDate, surcharge, surchargeType);
	}

	private Policy policy(Vehicle vehicle, String policyNumber, LocalDate startDate, LocalDate endDate,
			boolean surcharge, SurchargeType surchargeType) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setIntermediary(papadopoulos);
		policy.setStartDate(startDate);
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal("180.50"));
		policy.setSurcharge(surcharge);
		policy.setSurchargeType(surchargeType);
		return policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
