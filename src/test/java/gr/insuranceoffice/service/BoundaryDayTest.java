package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

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

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.ExpiringPolicyDto;
import gr.insuranceoffice.dto.ExpiryPeriod;
import gr.insuranceoffice.dto.OwnersSubmissionDto;
import gr.insuranceoffice.dto.PolicyFormDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
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
 * The renewal's first day. Policies may touch (Task 11d), so on the day the
 * old policy ends and the new one starts, both satisfy «τρέχον» =
 * CURRENT_DATE BETWEEN start_date AND end_date. Nothing may assume a vehicle
 * has at most one current policy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class BoundaryDayTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private VehicleService vehicleService;

	@Autowired
	private CustomerService customerService;

	@Autowired
	private OwnershipService ownershipService;

	@Autowired
	private PolicyService policyService;

	@Autowired
	private SearchService searchService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Customer maria;

	private Vehicle vehicle;

	private Policy ending;

	private Policy starting;

	@BeforeEach
	void renewedToday() {
		truncateTables();
		maria = customer("Αλεξίου", "Μαρία", "6900000001");
		vehicle = vehicle();
		owns(vehicle, maria);
		// Ends today, and the renewal starts today: both are in force.
		ending = policy("2100000001", TODAY.minusYears(1), TODAY);
		starting = policy("2100000002", TODAY, TODAY.plusDays(60));
		assertThat(policyRepository.isInsuredOn(vehicle.getId(), TODAY)).isTrue();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	// The ending policy is renewed, so it is not work to do, in any view.
	@Test
	void dashboardListsOnlyTheRenewalAndOnlyOnce() {
		assertThat(ids(ExpiryPeriod.DAYS_7)).isEmpty();
		assertThat(ids(ExpiryPeriod.DAYS_90)).containsExactly(starting.getId());
		// Still in force today, so not expired either.
		assertThat(ids(ExpiryPeriod.EXPIRED)).isEmpty();
	}

	@Test
	void vehicleCardShowsBothWithTheirOwnStatus() throws Exception {
		assertThat(vehicleService.findDetail(vehicle.getId()).policies())
				.extracting(PolicyViewDto::policyNumber, PolicyViewDto::status)
				.containsExactly(
						tuple("2100000002", PolicyStatus.ACTIVE),
						tuple("2100000001", PolicyStatus.EXPIRING));

		String html = mockMvc.perform(get("/vehicles/{id}", vehicle.getId()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(html.indexOf("2100000002")).isPositive().isLessThan(html.indexOf("2100000001"));
		// Both are in force, so both stand out.
		assertThat(Pattern.compile("table-primary").matcher(html).results().count()).isEqualTo(2);
	}

	@Test
	void customerCardShowsBoth() throws Exception {
		assertThat(customerService.findDetail(maria.getId()).policies())
				.extracting(PolicyViewDto::policyNumber)
				.containsExactly("2100000002", "2100000001");
		mockMvc.perform(get("/customers/{id}", maria.getId())).andExpect(status().isOk());
	}

	// DECISIONS §1 from the customer side: two matching policies, one answer.
	@Test
	void mobileRuleStillHoldsForTheCustomer() {
		CustomerDto current = customerService.find(maria.getId());
		CustomerDto withoutMobile = new CustomerDto(current.id(), current.taxId(), current.entityType(),
				current.lastName(), current.firstName(), null, null, null, null, null, null, null, null, null,
				null, null, current.version());

		assertThatThrownBy(() -> customerService.update(maria.getId(), withoutMobile))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("κινητό");
	}

	// DECISIONS §1 from the ownership side.
	@Test
	void mobileRuleStillHoldsForANewPrimaryOwner() {
		Customer anna = customer("Γεωργίου", "Άννα", null);
		OwnersSubmissionDto annaPrimary = new OwnersSubmissionDto(vehicle.getVersion(), TODAY, anna.getId(),
				List.of(maria.getId(), anna.getId()), List.of("50", "50"));

		assertThatThrownBy(() -> ownershipService.saveOwners(vehicle.getId(), annaPrimary))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("δεν έχει κινητό");
	}

	// Touching is not overlapping (Task 11d), so either policy can still be
	// edited on the day they meet.
	@Test
	void bothPoliciesCanStillBeEdited() {
		policyService.update(ending.getId(), withPremium(policyService.find(ending.getId()), "150,00"));
		policyService.update(starting.getId(), withPremium(policyService.find(starting.getId()), "199,90"));

		assertThat(policyRepository.findById(ending.getId())).get().extracting(Policy::getPremium)
				.isEqualTo(new BigDecimal("150.00"));
		assertThat(policyRepository.findById(starting.getId())).get().extracting(Policy::getPremium)
				.isEqualTo(new BigDecimal("199.90"));
	}

	// A third policy over the same day is still an overlap.
	@Test
	void aThirdPolicyOnThatDayIsStillAnOverlap() {
		PolicyFormDto third = new PolicyFormDto(null, vehicle.getId(), "2100000003", "Northwind", null,
				TODAY.minusDays(1), TODAY.plusDays(1), "180,00", false, null, null);

		assertThatThrownBy(() -> policyService.create(vehicle.getId(), third))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("2100000001")
				.hasMessageContaining("2100000002");
	}

	// Either policy number finds the vehicle, once.
	@Test
	void searchFindsTheVehicleOnceByEitherNumber() {
		assertThat(searchService.search("2100000001").vehicles()).hasSize(1);
		assertThat(searchService.search("2100000002").vehicles()).hasSize(1);
	}

	private List<Long> ids(ExpiryPeriod period) {
		return dashboardService.expiries(period, null).policies().stream().map(ExpiringPolicyDto::policyId).toList();
	}

	private static PolicyFormDto withPremium(PolicyFormDto form, String premium) {
		return new PolicyFormDto(form.id(), form.vehicleId(), form.policyNumber(), form.insuranceCompany(),
				form.intermediaryId(), form.startDate(), form.endDate(), premium, form.surcharge(),
				form.surchargeType(), form.version());
	}

	private Customer customer(String lastName, String firstName, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
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

	private Policy policy(String policyNumber, LocalDate startDate, LocalDate endDate) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(startDate);
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal("180.00"));
		return policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
