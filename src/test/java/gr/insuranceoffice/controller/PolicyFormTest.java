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
import java.util.Map;

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

/** Task 11d: creating and editing a policy from the vehicle card. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class PolicyFormTest {

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

	@BeforeEach
	void startWithAnInsurableVehicle() {
		truncateTables();
		vehicle = vehicle();
		owns(vehicle, customer("6900000001"));
		papadopoulos = intermediary("Παπαδόπουλος Νίκος", true);
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void opensAnEmptyFormFromTheVehicleCard() throws Exception {
		intermediary("Αποσυρμένος Γιώργος", false);

		assertThat(html(get("/vehicles/{id}", vehicle.getId())))
				.contains("href=\"/vehicles/" + vehicle.getId() + "/policies/new\"", "Νέο συμβόλαιο");
		String html = html(get("/vehicles/{id}/policies/new", vehicle.getId()));

		assertThat(html).contains("Νέο συμβόλαιο", "ΑΒΕ1234", "Αρ. Συμβολαίου", "Ασφαλιστική Εταιρεία",
				"Έναρξη Ασφάλειας", "Λήξη Ασφάλειας", "Ασφάλιστρο",
				// Existing intermediaries only, never free text (Task 11d).
				"<select class=\"form-select\" id=\"intermediaryId\" name=\"intermediaryId\"",
				"Παπαδόπουλος Νίκος",
				"<option value=\"ΝΕΟΣ_ΟΔΗΓΟΣ\"", "<option value=\"ΗΛΙΚΙΑΣ\"", "<option value=\"ΑΛΛΟ\"")
				// An intermediary no longer active is not offered for a new policy.
				.doesNotContain("Αποσυρμένος Γιώργος");
		// No duration is assumed: the dates start empty.
		assertThat(html).containsPattern("id=\"endDate\" name=\"endDate\" value=\"\"");
	}

	@Test
	void createsAPolicyAndLandsOnTheCard() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("intermediaryId", papadopoulos.getId().toString());
		values.set("surcharge", "true");
		values.set("surchargeType", "ΝΕΟΣ_ΟΔΗΓΟΣ");

		mockMvc.perform(create(values)).andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

		Policy stored = policyRepository.findByPolicyNumber("2100000001").orElseThrow();
		assertThat(stored.getInsuranceCompany()).isEqualTo("Northwind");
		assertThat(stored.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(stored.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(stored.getPremium()).isEqualByComparingTo("180.50");
		assertThat(stored.isSurcharge()).isTrue();
		assertThat(stored.getSurchargeType()).isEqualTo(SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ);
		assertThat(stored.getVersion()).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT intermediary_id FROM policy", Long.class))
				.isEqualTo(papadopoulos.getId());
	}

	// SPEC §8: a number with two decimals, the Greek comma accepted.
	@ParameterizedTest(name = "«{0}» → {1}")
	@CsvSource(delimiter = '|', value = {
			"180,50|180.50",
			"180.5|180.50",
			"1.234,50|1234.50",
			"180,00 €|180.00" })
	void readsThePremiumAsTyped(String typed, String stored) throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("premium", typed);

		mockMvc.perform(create(values)).andExpect(status().is3xxRedirection());

		assertThat(policyRepository.findByPolicyNumber("2100000001")).get()
				.extracting(Policy::getPremium).isEqualTo(new BigDecimal(stored));
	}

	@ParameterizedTest(name = "«{0}»")
	@CsvSource(delimiter = '|', value = {
			"εκατό|Συμπληρώστε ποσό, π.χ. 180,50.",
			"180,505|Το ασφάλιστρο έχει το πολύ δύο δεκαδικά.",
			"0|Το ασφάλιστρο πρέπει να είναι θετικό ποσό." })
	void refusesAPremiumThatIsNotAnAmountAndKeepsIt(String typed, String message) throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("premium", typed);

		assertThat(html(create(values))).contains(message, "value=\"" + typed + "\"");
		assertThat(policyRepository.count()).isZero();
	}

	@Test
	void showsEveryMissingFieldAndKeepsWhatWasTyped() throws Exception {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("insuranceCompany", "Northwind");

		assertThat(html(create(values))).contains(
				"Ο αριθμός συμβολαίου είναι υποχρεωτικός.",
				"Η ημερομηνία έναρξης είναι υποχρεωτική.",
				"Η ημερομηνία λήξης είναι υποχρεωτική.",
				"Το ασφάλιστρο είναι υποχρεωτικό.",
				"value=\"Northwind\"");
	}

	@Test
	void refusesAPolicyNumberAlreadyUsed() throws Exception {
		mockMvc.perform(create(valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> later = valid();
		later.set("startDate", "2026-09-01");
		later.set("endDate", "2027-03-01");

		assertThat(html(create(later))).contains("Υπάρχει ήδη συμβόλαιο με αυτόν τον αριθμό.");
		assertThat(policyRepository.count()).isEqualTo(1);
	}

	@Test
	void refusesAnEndThatIsNotAfterTheStart() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("endDate", "2026-03-01");

		assertThat(html(create(values))).contains("Η λήξη πρέπει να είναι μετά την έναρξη.");
	}

	@Test
	void refusesAnOverlappingPolicy() throws Exception {
		mockMvc.perform(create(valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> overlapping = valid();
		overlapping.set("policyNumber", "2100000002");
		overlapping.set("startDate", "2026-08-01");
		overlapping.set("endDate", "2027-02-01");

		assertThat(html(create(overlapping)))
				.contains("Επικαλύπτεται με το συμβόλαιο 2100000001 (01/03/2026 – 01/09/2026).");
		assertThat(policyRepository.count()).isEqualTo(1);
	}

	// Task 11d decision: a renewal may start on the day the previous one ends.
	@Test
	void acceptsAPolicyThatStartsTheDayThePreviousOneEnds() throws Exception {
		mockMvc.perform(create(valid())).andExpect(status().is3xxRedirection());

		MultiValueMap<String, String> renewal = valid();
		renewal.set("policyNumber", "2100000002");
		renewal.set("startDate", "2026-09-01");
		renewal.set("endDate", "2027-09-01");

		mockMvc.perform(create(renewal)).andExpect(status().is3xxRedirection());
		assertThat(policyRepository.count()).isEqualTo(2);
	}

	@Test
	void takesASurchargeTypeOnlyWithASurcharge() throws Exception {
		MultiValueMap<String, String> typeWithout = valid();
		typeWithout.set("surchargeType", "ΗΛΙΚΙΑΣ");
		assertThat(html(create(typeWithout))).contains("Τύπος επασφαλίστρου μόνο όταν υπάρχει επασφάλιστρο");

		MultiValueMap<String, String> surchargeWithout = valid();
		surchargeWithout.set("surcharge", "true");
		assertThat(html(create(surchargeWithout))).contains("Επιλέξτε τον τύπο του επασφαλίστρου.");

		assertThat(policyRepository.count()).isZero();
	}

	// DECISIONS §1, from the policy side (NOTES "Mobile rule from the other side").
	@Test
	void refusesACurrentPolicyWhenThePrimaryOwnerHasNoMobile() throws Exception {
		Vehicle other = vehicle("ΚΜΝ-4321", "WVWZZZ1KZAW654321");
		owns(other, customer(null));
		MultiValueMap<String, String> current = valid();
		current.set("startDate", TODAY.minusMonths(1).toString());
		current.set("endDate", TODAY.plusMonths(5).toString());

		assertThat(html(create(other, current))).contains("δεν έχει κινητό");
		assertThat(policyRepository.count()).isZero();

		// A policy that is not yet in force is allowed.
		MultiValueMap<String, String> future = valid();
		future.set("startDate", TODAY.plusDays(10).toString());
		future.set("endDate", TODAY.plusMonths(6).toString());
		mockMvc.perform(create(other, future)).andExpect(status().is3xxRedirection());
	}

	@Test
	void editsAPolicyWithoutClashingWithItself() throws Exception {
		mockMvc.perform(create(valid())).andExpect(status().is3xxRedirection());
		Policy stored = policyRepository.findByPolicyNumber("2100000001").orElseThrow();

		assertThat(html(get("/vehicles/{id}", vehicle.getId())))
				.contains("href=\"/policies/" + stored.getId() + "/edit\"");
		assertThat(html(get("/policies/{id}/edit", stored.getId())))
				.contains("Επεξεργασία συμβολαίου", "value=\"2100000001\"", "value=\"180,50\"",
						"value=\"2026-03-01\"", "name=\"version\" value=\"0\"");

		MultiValueMap<String, String> values = valid();
		values.set("version", "0");
		values.set("endDate", "2026-09-15");
		values.set("premium", "199,90");
		mockMvc.perform(update(stored.getId(), values)).andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

		assertThat(policyRepository.findById(stored.getId())).get()
				.extracting(Policy::getEndDate, Policy::getPremium, Policy::getVersion)
				.containsExactly(LocalDate.of(2026, 9, 15), new BigDecimal("199.90"), 1L);
	}

	// An unticked checkbox sends nothing; that must still clear the surcharge.
	@Test
	void clearsTheSurchargeWhenItIsUntickedOnEdit() throws Exception {
		MultiValueMap<String, String> values = valid();
		values.set("surcharge", "true");
		values.set("surchargeType", "ΗΛΙΚΙΑΣ");
		mockMvc.perform(create(values)).andExpect(status().is3xxRedirection());
		Policy stored = policyRepository.findByPolicyNumber("2100000001").orElseThrow();

		MultiValueMap<String, String> unticked = valid();
		unticked.set("version", "0");
		mockMvc.perform(update(stored.getId(), unticked)).andExpect(status().is3xxRedirection());

		assertThat(policyRepository.findById(stored.getId())).get()
				.extracting(Policy::isSurcharge, Policy::getSurchargeType)
				.containsExactly(false, null);
	}

	// SPEC §9: the second writer is told, and nothing is overwritten.
	@Test
	void refusesToSaveOverSomeoneElsesChange() throws Exception {
		mockMvc.perform(create(valid())).andExpect(status().is3xxRedirection());
		Policy stored = policyRepository.findByPolicyNumber("2100000001").orElseThrow();
		stored.setPremium(new BigDecimal("150.00"));
		policyRepository.save(stored);

		MultiValueMap<String, String> values = valid();
		values.set("version", "0");
		values.set("premium", "199,90");

		String html = mockMvc.perform(update(stored.getId(), values))
				.andExpect(status().isOk()).andExpect(view().name("policy-form"))
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("άλλαξε από άλλον χρήστη", "value=\"199,90\"");
		assertThat(policyRepository.findById(stored.getId())).get().extracting(Policy::getPremium)
				.isEqualTo(new BigDecimal("150.00"));
	}

	// Editing an old policy keeps its intermediary even if no longer active.
	@Test
	void keepsAnInactiveIntermediaryOnThePolicyThatNamesIt() throws Exception {
		Intermediary retired = intermediary("Αποσυρμένος Γιώργος", true);
		MultiValueMap<String, String> values = valid();
		values.set("intermediaryId", retired.getId().toString());
		mockMvc.perform(create(values)).andExpect(status().is3xxRedirection());
		retired.setActive(false);
		intermediaryRepository.save(retired);
		Policy stored = policyRepository.findByPolicyNumber("2100000001").orElseThrow();

		assertThat(html(get("/policies/{id}/edit", stored.getId())))
				.containsPattern("value=\"" + retired.getId() + "\"[^>]*selected[^>]*>Αποσυρμένος Γιώργος");
	}

	@Test
	void answers404ForAVehicleOrPolicyThatDoesNotExist() throws Exception {
		mockMvc.perform(get("/vehicles/{id}/policies/new", 999)).andExpect(status().isNotFound());
		mockMvc.perform(get("/policies/{id}/edit", 999)).andExpect(status().isNotFound());
	}

	// A six-month policy, as some of the office's are.
	private static MultiValueMap<String, String> valid() {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.setAll(Map.of(
				"policyNumber", "2100000001",
				"insuranceCompany", "Northwind",
				"startDate", "2026-03-01",
				"endDate", "2026-09-01",
				"premium", "180,50"));
		return values;
	}

	private MockHttpServletRequestBuilder create(MultiValueMap<String, String> values) {
		return create(vehicle, values);
	}

	private static MockHttpServletRequestBuilder create(Vehicle vehicle, MultiValueMap<String, String> values) {
		return post("/vehicles/{id}/policies", vehicle.getId()).with(csrf()).params(values)
				.param("vehicleId", vehicle.getId().toString());
	}

	private MockHttpServletRequestBuilder update(Long policyId, MultiValueMap<String, String> values) {
		return post("/policies/{id}", policyId).with(csrf()).params(values)
				.param("id", policyId.toString()).param("vehicleId", vehicle.getId().toString());
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer(String mobile) {
		Customer customer = new Customer();
		customer.setLastName("Αλεξίου");
		customer.setFirstName("Μαρία");
		customer.setMobile(mobile);
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

	private Intermediary intermediary(String fullName, boolean active) {
		Intermediary intermediary = new Intermediary();
		intermediary.setFullName(fullName);
		intermediary.setActive(active);
		return intermediaryRepository.save(intermediary);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
