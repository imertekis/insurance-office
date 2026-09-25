package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Stubber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
 * Task 18: a save that a unique index refuses after the service's own check
 * passed comes back as the form, with the message beside the field and what
 * the clerk typed, instead of an error page; and the ownership form says by
 * name who cannot join again on the same day.
 * <p>
 * The race is played out without threads: the service's check is stubbed so
 * that another clerk's row is committed, in a transaction of its own, right
 * after the check ran and found nothing. The spies make a context of their
 * own, so every form's case is in this one class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class UniqueViolationFormTest {

	private static final LocalDate TODAY = LocalDate.now();

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final String TAX_ID_TAKEN = "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ.";

	private static final String VIN_TAKEN = "Υπάρχει ήδη όχημα με αυτό το VIN.";

	private static final String PLATE_TAKEN =
			"Υπάρχει ήδη όχημα με αυτή την πινακίδα· τα ελληνικά και τα λατινικά γράμματα μετρούν ως ίδια.";

	private static final String POLICY_NUMBER_TAKEN = "Υπάρχει ήδη συμβόλαιο με αυτόν τον αριθμό.";

	@Autowired
	private MockMvc mockMvc;

	@MockitoSpyBean
	private CustomerRepository customerRepository;

	@MockitoSpyBean
	private VehicleRepository vehicleRepository;

	@MockitoSpyBean
	private PolicyRepository policyRepository;

	@MockitoSpyBean
	private OwnershipRepository ownershipRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

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
	void newCustomerWithATaxIdAnotherClerkJustSaved() throws Exception {
		anotherClerkSaves(() -> customer("Άλλος", "Πελάτης", "123456783", null), Optional.empty())
				.when(customerRepository).findByTaxId("123456783");

		String html = html(post("/customers").with(csrf()).param("entityType", "INDIVIDUAL")
				.param("lastName", "Παπαδόπουλος").param("firstName", "Γιώργος")
				.param("taxId", "123456783").param("mobile", "6900000009"));

		assertFieldError(html, "taxId", TAX_ID_TAKEN);
		assertThat(html).contains("value=\"Παπαδόπουλος\"", "value=\"Γιώργος\"", "value=\"6900000009\"");
		// Only the other clerk's customer.
		assertThat(customerRepository.findAll()).extracting(Customer::getLastName).containsExactly("Άλλος");
	}

	@Test
	void editedCustomerWithATaxIdAnotherClerkJustSaved() throws Exception {
		Customer maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		anotherClerkSaves(() -> customer("Άλλος", "Πελάτης", "123456783", null), Optional.empty())
				.when(customerRepository).findByTaxId("123456783");

		String html = html(editCustomer(maria, "123456783"));

		assertFieldError(html, "taxId", TAX_ID_TAKEN);
		assertThat(html).contains("value=\"Αλεξίου-Παππά\"", "value=\"123456783\"");
		assertThat(customerRepository.findById(maria.getId())).get()
				.extracting(Customer::getLastName, Customer::getTaxId, Customer::getVersion)
				.containsExactly("Αλεξίου", "900000080", 0L);

		// The rollback left the version as the form has it, so once the ΑΦΜ
		// is put right the same form saves, without a conflict.
		mockMvc.perform(editCustomer(maria, "900000091"))
				.andExpect(redirectedUrl("/customers/" + maria.getId()));
		assertThat(customerRepository.findById(maria.getId())).get()
				.extracting(Customer::getLastName, Customer::getTaxId)
				.containsExactly("Αλεξίου-Παππά", "900000091");
	}

	@Test
	void newVehicleWithAVinAnotherClerkJustSaved() throws Exception {
		anotherClerkSaves(() -> vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW123456"), Optional.empty())
				.when(vehicleRepository).findByVin("WVWZZZ1KZAW123456");

		String html = html(post("/vehicles").with(csrf()).params(vehicleForm("ΑΒΕ-1234", "WVWZZZ1KZAW123456")));

		assertFieldError(html, "vin", VIN_TAKEN);
		assertThat(html).contains("value=\"ΑΒΕ-1234\"", "value=\"Golf\"", "value=\"Μαύρο\"");
		assertThat(vehicleRepository.findAll()).extracting(Vehicle::getPlate).containsExactly("ΚΑΒ9999");
	}

	@Test
	void newVehicleWithAPlateAnotherClerkJustSaved() throws Exception {
		// Latin letters: the same plate as the Greek one typed below.
		anotherClerkSaves(() -> vehicle("ABE-1234", "WVWZZZ1KZAW654321"), Optional.empty())
				.when(vehicleRepository).findByPlateNormalized("ABE1234");

		String html = html(post("/vehicles").with(csrf()).params(vehicleForm("ΑΒΕ-1234", "WVWZZZ1KZAW123456")));

		assertFieldError(html, "plate", PLATE_TAKEN);
		assertThat(html).contains("value=\"ΑΒΕ-1234\"", "value=\"WVWZZZ1KZAW123456\"");
		assertThat(vehicleRepository.findAll()).extracting(Vehicle::getVin).containsExactly("WVWZZZ1KZAW654321");
	}

	@Test
	void editedVehicleWithAVinAnotherClerkJustSaved() throws Exception {
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		anotherClerkSaves(() -> vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW654321"), Optional.empty())
				.when(vehicleRepository).findByVin("WVWZZZ1KZAW654321");

		String html = html(editVehicle(golf, "ΑΒΕ-1234", "WVWZZZ1KZAW654321"));

		assertFieldError(html, "vin", VIN_TAKEN);
		assertThat(html).contains("value=\"WVWZZZ1KZAW654321\"", "value=\"Μαύρο\"");
		assertThat(vehicleRepository.findById(golf.getId())).get()
				.extracting(Vehicle::getVin, Vehicle::getColor, Vehicle::getVersion)
				.containsExactly("WVWZZZ1KZAW123456", "Λευκό", 0L);

		mockMvc.perform(editVehicle(golf, "ΑΒΕ-1234", "WVWZZZ1KZAW111111"))
				.andExpect(redirectedUrl("/vehicles/" + golf.getId()));
		assertThat(vehicleRepository.findById(golf.getId())).get()
				.extracting(Vehicle::getVin, Vehicle::getColor)
				.containsExactly("WVWZZZ1KZAW111111", "Μαύρο");
	}

	@Test
	void editedVehicleWithAPlateAnotherClerkJustSaved() throws Exception {
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		anotherClerkSaves(() -> vehicle("ΝΚΝ-7777", "WVWZZZ1KZAW654321"), Optional.empty())
				.when(vehicleRepository).findByPlateNormalized("NKN7777");

		String html = html(editVehicle(golf, "ΝΚΝ-7777", "WVWZZZ1KZAW123456"));

		assertFieldError(html, "plate", PLATE_TAKEN);
		assertThat(html).contains("value=\"ΝΚΝ-7777\"", "value=\"Μαύρο\"");
		assertThat(vehicleRepository.findById(golf.getId())).get()
				.extracting(Vehicle::getPlate, Vehicle::getColor)
				.containsExactly("ΑΒΕ1234", "Λευκό");
	}

	@Test
	void newPolicyWithANumberAnotherClerkJustSaved() throws Exception {
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		Vehicle polo = vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW654321");
		anotherClerkSaves(() -> policy(polo, "2100000001", TODAY.minusYears(3), TODAY.minusYears(2)),
				Optional.empty()).when(policyRepository).findByPolicyNumber("2100000001");

		String html = html(post("/vehicles/{id}/policies", golf.getId()).with(csrf())
				.params(policyForm(golf, "2100000001")));

		assertFieldError(html, "policyNumber", POLICY_NUMBER_TAKEN);
		assertThat(html).contains("value=\"2100000001\"", "value=\"195,00\"");
		assertThat(policyRepository.findAll()).extracting(policy -> policy.getVehicle().getId())
				.containsExactly(polo.getId());
	}

	@Test
	void editedPolicyWithANumberAnotherClerkJustSaved() throws Exception {
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		Vehicle polo = vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW654321");
		Policy current = policy(golf, "2100000001", TODAY.minusMonths(1), TODAY.plusMonths(11));
		anotherClerkSaves(() -> policy(polo, "2100000002", TODAY.minusYears(3), TODAY.minusYears(2)),
				Optional.empty()).when(policyRepository).findByPolicyNumber("2100000002");

		String html = html(editPolicy(current, golf, "2100000002"));

		assertFieldError(html, "policyNumber", POLICY_NUMBER_TAKEN);
		assertThat(html).contains("value=\"2100000002\"", "value=\"195,00\"");
		assertThat(policyRepository.findById(current.getId())).get()
				.extracting(Policy::getPolicyNumber, Policy::getVersion)
				.containsExactly("2100000001", 0L);
		assertThat(policyRepository.findById(current.getId()).orElseThrow().getPremium())
				.isEqualByComparingTo("180.50");

		mockMvc.perform(editPolicy(current, golf, "2100000003"))
				.andExpect(redirectedUrl("/vehicles/" + golf.getId()));
		assertThat(policyRepository.findById(current.getId())).get()
				.extracting(Policy::getPolicyNumber).isEqualTo("2100000003");
	}

	@Test
	void renewalWithANumberAnotherClerkJustSaved() throws Exception {
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		Vehicle polo = vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW654321");
		Policy current = policy(golf, "2100000001", TODAY.minusMonths(11), TODAY.plusMonths(1));
		anotherClerkSaves(() -> policy(polo, "2100000002", TODAY.minusYears(3), TODAY.minusYears(2)),
				Optional.empty()).when(policyRepository).findByPolicyNumber("2100000002");

		MultiValueMap<String, String> renewal = policyForm(golf, "2100000002");
		renewal.set("startDate", current.getEndDate().toString());
		renewal.set("endDate", current.getEndDate().plusYears(1).toString());
		String html = html(post("/vehicles/{id}/policies", golf.getId()).with(csrf()).params(renewal)
				.param("renewalOf", "2100000001"));

		assertFieldError(html, "policyNumber", POLICY_NUMBER_TAKEN);
		// Still a renewal, with what the clerk typed.
		assertThat(html).contains("Ανανέωση συμβολαίου", "ανανέωση του 2100000001", "value=\"195,00\"",
				"value=\"" + current.getEndDate().plusYears(1) + "\"");
		// The renewed policy, and the other clerk's on another vehicle; not
		// findByPolicyNumber, which is still stubbed.
		assertThat(policyRepository.findAll())
				.extracting(Policy::getPolicyNumber, policy -> policy.getVehicle().getId())
				.containsExactlyInAnyOrder(tuple("2100000001", golf.getId()), tuple("2100000002", polo.getId()));
	}

	// Two clerks add the same owner on the same day: the second one's check
	// found nothing, and the index refuses the row.
	@Test
	void ownerAnotherClerkJustAddedOnTheSameDay() throws Exception {
		Customer maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		Customer nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		owns(golf, maria, "100", true, null, null);
		anotherClerkSaves(() -> owns(golf, nikos, "50", false, TODAY, null), List.of())
				.when(ownershipRepository).findStartingOn(eq(golf.getId()), any(), eq(TODAY));

		String html = html(saveOwners(golf, List.of(maria, nikos), List.of("50", "50"), maria, TODAY));

		assertFieldError(html, "transferDate", "Οι ιδιοκτήτες του οχήματος άλλαξαν από άλλον χρήστη ενώ τους "
				+ "επεξεργαζόσασταν. Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές και επαναλάβετε τη δική σας.");
		// The rows as the clerk left them, marked as not saved (Task 16f-2).
		assertThat(html).contains("Αλεξίου Μαρία", "Βασιλείου Νίκος", "data-unsaved=\"true\"");
		// Maria's share is untouched; the one row of Nikos is the other clerk's.
		assertThat(ownershipRepository.findByVehicleIdWithCustomer(golf.getId()))
				.extracting(o -> o.getCustomer().getId(), Ownership::getPercentage, Ownership::getFromDate)
				.containsExactlyInAnyOrder(tuple(maria.getId(), new BigDecimal("100.00"), null),
						tuple(nikos.getId(), new BigDecimal("50.00"), TODAY));
	}

	// The reproduced case: added and removed with the same transfer date,
	// then added back with it. Not a race: the check names the customer.
	@Test
	void ownerRemovedAndAddedBackWithTheSameTransferDate() throws Exception {
		Customer maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		Customer nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		owns(golf, maria, "100", true, null, null);
		saved(saveOwners(golf, List.of(maria, nikos), List.of("50", "50"), maria, TODAY), golf);
		saved(saveOwners(golf, List.of(maria), List.of("100"), maria, TODAY), golf);

		String html = html(saveOwners(golf, List.of(maria, nikos), List.of("50", "50"), maria, TODAY));

		assertFieldError(html, "transferDate", "Ο πελάτης Βασιλείου Νίκος αφαιρέθηκε από αυτό το όχημα με "
				+ "ημερομηνία μεταβίβασης " + TODAY.format(GREEK_DATE) + ", την ίδια με αυτή που δώσατε, και δεν "
				+ "μπορεί να ξαναμπεί με την ίδια ημερομηνία. Αν η αφαίρεση ήταν λάθος, ζητήστε από τον διαχειριστή "
				+ "να τη διαγράψει από τους πρώην ιδιοκτήτες στην καρτέλα του οχήματος και αποθηκεύστε ξανά.");
		assertThat(html).contains("Αλεξίου Μαρία", "Βασιλείου Νίκος", "data-unsaved=\"true\"");
		Ownership removed = ownershipRepository.findByVehicleIdWithCustomer(golf.getId()).stream()
				.filter(o -> o.getCustomer().getId().equals(nikos.getId())).findFirst().orElseThrow();
		assertThat(removed).extracting(Ownership::getFromDate, Ownership::getToDate).containsExactly(TODAY, TODAY);
		assertThat(ownershipRepository.count()).isEqualTo(2);

		// As the message says: the administrator deletes the mistaken row, and
		// the same form saves.
		mockMvc.perform(post("/ownerships/{id}/delete", removed.getId()).with(csrf())
				.with(user("διαχειριστής").roles("ΔΙΑΧΕΙΡΙΣΤΗΣ")))
				.andExpect(status().is3xxRedirection());
		saved(saveOwners(golf, List.of(maria, nikos), List.of("50", "50"), maria, TODAY), golf);
		assertThat(ownershipRepository.findCurrentByVehicleIdWithCustomer(golf.getId()))
				.extracting(o -> o.getCustomer().getId(), Ownership::isPrimary, Ownership::getFromDate)
				.containsExactly(tuple(maria.getId(), true, null), tuple(nikos.getId(), false, TODAY));
	}

	@Test
	void formerOwnerAddedBackOnTheDayTheirOwnershipStarted() throws Exception {
		Customer maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		Customer nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		Vehicle golf = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		LocalDate bought = TODAY.minusDays(30);
		LocalDate sold = TODAY.minusDays(10);
		owns(golf, nikos, "100", true, bought, sold);
		owns(golf, maria, "100", true, sold, null);

		String html = html(saveOwners(golf, List.of(maria, nikos), List.of("50", "50"), maria, bought));

		assertFieldError(html, "transferDate", "Ο πελάτης Βασιλείου Νίκος ήταν ιδιοκτήτης αυτού του οχήματος από "
				+ bought.format(GREEK_DATE) + " έως " + sold.format(GREEK_DATE) + ". Νέα ιδιοκτησία του δεν "
				+ "μπορεί να αρχίζει στις " + bought.format(GREEK_DATE) + "· ελέγξτε την ημερομηνία μεταβίβασης.");
		assertThat(html).contains("Βασιλείου Νίκος", "value=\"" + bought + "\"");
		assertThat(ownershipRepository.findCurrentByVehicleIdWithCustomer(golf.getId()))
				.extracting(o -> o.getCustomer().getId(), Ownership::getPercentage)
				.containsExactly(tuple(maria.getId(), new BigDecimal("100.00")));
		assertThat(ownershipRepository.count()).isEqualTo(2);
	}

	/**
	 * The check the service runs before saving, answered as it was just
	 * before another clerk committed the same value: nothing there. The
	 * other clerk's row is committed, in a transaction of its own, the first
	 * time the check runs, so the save that follows breaks the unique index.
	 * {@code callRealMethod()} cannot be used: the spy wraps a Spring Data
	 * proxy, and Mockito refuses it.
	 */
	private Stubber anotherClerkSaves(Runnable save, Object checkFinds) {
		AtomicBoolean saved = new AtomicBoolean();
		return doAnswer(invocation -> {
			if (saved.compareAndSet(false, true)) {
				TransactionTemplate otherClerk = new TransactionTemplate(transactionManager);
				otherClerk.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
				otherClerk.executeWithoutResult(status -> save.run());
			}
			return checkFinds;
		});
	}

	/** The message beside the field, as the form renders a service's check. */
	private static void assertFieldError(String html, String field, String message) {
		Matcher input = Pattern.compile("<input[^>]*name=\"" + field + "\"[^>]*>").matcher(html);
		assertThat(input.find()).as(field).isTrue();
		assertThat(input.group()).contains("is-invalid");
		assertThat(html).containsPattern(Pattern.compile("name=\"" + field + "\"[^>]*>\\s*"
				+ "<div class=\"invalid-feedback\">" + Pattern.quote(message) + "</div>"));
	}

	private MockHttpServletRequestBuilder editCustomer(Customer customer, String taxId) {
		return post("/customers/{id}", customer.getId()).with(csrf())
				.param("id", customer.getId().toString()).param("version", "0")
				.param("entityType", "INDIVIDUAL").param("lastName", "Αλεξίου-Παππά").param("firstName", "Μαρία")
				.param("taxId", taxId).param("mobile", "6900000001");
	}

	private static MultiValueMap<String, String> vehicleForm(String plate, String vin) {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("plate", plate);
		values.set("vin", vin);
		values.set("brand", "Volkswagen");
		values.set("model", "Golf");
		values.set("firstRegistration", "2012-05-14");
		values.set("category", "M1");
		values.set("usageType", "ΕΙΧ");
		values.set("color", "Μαύρο");
		values.set("engineCc", "1598");
		values.set("powerKw", "81");
		values.set("fuelType", "ΒΕΝΖΙΝΗ");
		return values;
	}

	private MockHttpServletRequestBuilder editVehicle(Vehicle vehicle, String plate, String vin) {
		return post("/vehicles/{id}", vehicle.getId()).with(csrf()).params(vehicleForm(plate, vin))
				.param("id", vehicle.getId().toString()).param("version", "0");
	}

	private static MultiValueMap<String, String> policyForm(Vehicle vehicle, String policyNumber) {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		values.set("vehicleId", vehicle.getId().toString());
		values.set("policyNumber", policyNumber);
		values.set("insuranceCompany", "Northwind");
		values.set("startDate", TODAY.minusMonths(1).toString());
		values.set("endDate", TODAY.plusMonths(11).toString());
		values.set("premium", "195,00");
		return values;
	}

	private MockHttpServletRequestBuilder editPolicy(Policy policy, Vehicle vehicle, String policyNumber) {
		return post("/policies/{id}", policy.getId()).with(csrf()).params(policyForm(vehicle, policyNumber))
				.param("id", policy.getId().toString()).param("version", "0");
	}

	// The vehicle's version is read afresh: every saved change of owners raises it.
	private MockHttpServletRequestBuilder saveOwners(Vehicle vehicle, List<Customer> customers,
			List<String> percentages, Customer primary, LocalDate transferDate) {
		Long version = vehicleRepository.findById(vehicle.getId()).orElseThrow().getVersion();
		MockHttpServletRequestBuilder request = post("/vehicles/{id}/owners", vehicle.getId()).with(csrf())
				.param("action", "save").param("vehicleVersion", version.toString())
				.param("transferDate", transferDate.toString()).param("primary", primary.getId().toString());
		customers.forEach(customer -> request.param("customerId", customer.getId().toString()));
		percentages.forEach(percentage -> request.param("percentage", percentage));
		return request;
	}

	private void saved(MockHttpServletRequestBuilder request, Vehicle vehicle) throws Exception {
		mockMvc.perform(request).andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));
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

	private Policy policy(Vehicle vehicle, String policyNumber, LocalDate startDate, LocalDate endDate) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(startDate);
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal("180.50"));
		return policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
