package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.DashboardDto;
import gr.insuranceoffice.dto.ExpiringPolicyDto;
import gr.insuranceoffice.dto.ExpiryPeriod;
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

/** The expiry screen's rules (SPEC §7.1), against PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DashboardServiceTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private DashboardService dashboardService;

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

	private int vehicles;

	private int policies;

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
	void showsTheNextThirtyDaysSoonestFirst() {
		Policy in30 = policy(vehicle(), TODAY.plusDays(30), "Northwind");
		Policy today = policy(vehicle(), TODAY, "Northwind");
		Policy in10 = policy(vehicle(), TODAY.plusDays(10), "Northwind");
		policy(vehicle(), TODAY.plusDays(31), "Northwind");
		policy(vehicle(), TODAY.minusDays(1), "Northwind");

		DashboardDto dashboard = dashboardService.expiries(ExpiryPeriod.DEFAULT, null);

		assertThat(dashboard.period()).isEqualTo(ExpiryPeriod.DAYS_30);
		assertThat(dashboard.from()).isEqualTo(TODAY);
		assertThat(dashboard.to()).isEqualTo(TODAY.plusDays(30));
		assertThat(dashboard.policies()).extracting(ExpiringPolicyDto::policyId)
				.containsExactly(today.getId(), in10.getId(), in30.getId());
	}

	@Test
	void coversSevenSixtyAndNinetyDaysIncludingTheLastDay() {
		Policy in7 = policy(vehicle(), TODAY.plusDays(7), "Northwind");
		Policy in8 = policy(vehicle(), TODAY.plusDays(8), "Northwind");
		Policy in60 = policy(vehicle(), TODAY.plusDays(60), "Northwind");
		Policy in61 = policy(vehicle(), TODAY.plusDays(61), "Northwind");
		Policy in90 = policy(vehicle(), TODAY.plusDays(90), "Northwind");
		policy(vehicle(), TODAY.plusDays(91), "Northwind");

		assertThat(ids(ExpiryPeriod.DAYS_7)).containsExactly(in7.getId());
		assertThat(ids(ExpiryPeriod.DAYS_60)).containsExactly(in7.getId(), in8.getId(), in60.getId());
		assertThat(ids(ExpiryPeriod.DAYS_90))
				.containsExactly(in7.getId(), in8.getId(), in60.getId(), in61.getId(), in90.getId());
	}

	// A renewal is work done: the screen lists the renewals still to do.
	@Test
	void leavesOutPoliciesThatHaveBeenRenewed() {
		Vehicle renewed = vehicle();
		policy(renewed, TODAY.plusDays(10), "Northwind");
		// Renewed early: starts on the old end date (six-month policy).
		policy(renewed, TODAY.plusDays(10), TODAY.plusDays(10).plusMonths(6), "Northwind");
		Policy notRenewed = policy(vehicle(), TODAY.plusDays(20), "Northwind");

		assertThat(ids(ExpiryPeriod.DAYS_30)).containsExactly(notRenewed.getId());
	}

	@Test
	void alreadyExpiredMeansNotRenewedWithinTheLastNinetyDays() {
		Policy yesterday = policy(vehicle(), TODAY.minusDays(1), "Northwind");
		Policy ninetyDaysAgo = policy(vehicle(), TODAY.minusDays(90), "Northwind");
		policy(vehicle(), TODAY.minusDays(91), "Northwind");
		// Still in force today, so expiring, not expired.
		policy(vehicle(), TODAY, "Northwind");
		Vehicle renewed = vehicle();
		policy(renewed, TODAY.minusDays(5), "Northwind");
		policy(renewed, TODAY.minusDays(5), TODAY.minusDays(5).plusYears(1), "Northwind");

		DashboardDto dashboard = dashboardService.expiries(ExpiryPeriod.EXPIRED, null);

		assertThat(dashboard.from()).isEqualTo(TODAY.minusDays(90));
		assertThat(dashboard.to()).isEqualTo(TODAY.minusDays(1));
		assertThat(dashboard.policies()).extracting(ExpiringPolicyDto::policyId)
				.containsExactly(ninetyDaysAgo.getId(), yesterday.getId());
	}

	@Test
	void filtersByInsuranceCompany() {
		Policy northwind = policy(vehicle(), TODAY.plusDays(5), "Northwind");
		Policy acme = policy(vehicle(), TODAY.plusDays(6), "ACME");
		policy(vehicle(), TODAY.plusDays(200), "Contoso");

		assertThat(dashboardService.expiries(ExpiryPeriod.DAYS_30, "ACME").policies())
				.extracting(ExpiringPolicyDto::policyId).containsExactly(acme.getId());
		assertThat(dashboardService.expiries(ExpiryPeriod.DAYS_30, " ").policies())
				.extracting(ExpiringPolicyDto::policyId).containsExactly(northwind.getId(), acme.getId());

		DashboardDto dashboard = dashboardService.expiries(ExpiryPeriod.DAYS_30, "ACME");
		assertThat(dashboard.insuranceCompany()).isEqualTo("ACME");
		// Every company, even one with nothing expiring in the period.
		assertThat(dashboard.insuranceCompanies()).containsExactly("ACME", "Contoso", "Northwind");
	}

	@Test
	void showsThePlateThePrimaryOwnerAndThePremium() {
		Vehicle vehicle = vehicle();
		Customer primary = customer("Αλεξίου", "Μαρία", "6900000001");
		owns(vehicle, primary, true, null);
		owns(vehicle, customer("Βασιλείου", "Νίκος", "6900000002"), false, null);
		// A former primary owner is not the one to call.
		owns(vehicle, customer("Γεωργίου", "Άννα", "6900000003"), true, TODAY.minusYears(1));
		Policy policy = policy(vehicle, TODAY.plusDays(3), "Northwind");

		assertThat(dashboardService.expiries(ExpiryPeriod.DAYS_7, null).policies()).containsExactly(
				new ExpiringPolicyDto(policy.getId(), vehicle.getId(), vehicle.getPlate(), primary.getId(), "Αλεξίου",
						"Μαρία", "6900000001", TODAY.plusDays(3), "Northwind", new BigDecimal("180.00")));
	}

	@Test
	void keepsAPolicyWhoseVehicleHasNoCurrentPrimaryOwner() {
		Vehicle vehicle = vehicle();
		owns(vehicle, customer("Γεωργίου", "Άννα", "6900000003"), true, TODAY.minusYears(1));
		policy(vehicle, TODAY.plusDays(3), "Northwind");

		assertThat(dashboardService.expiries(ExpiryPeriod.DAYS_7, null).policies()).singleElement()
				.satisfies(row -> {
					assertThat(row.plate()).isEqualTo(vehicle.getPlate());
					assertThat(row.customerId()).isNull();
					assertThat(row.mobile()).isNull();
				});
	}

	private List<Long> ids(ExpiryPeriod period) {
		return dashboardService.expiries(period, null).policies().stream().map(ExpiringPolicyDto::policyId).toList();
	}

	private Vehicle vehicle() {
		vehicles++;
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW1%05d".formatted(vehicles));
		vehicle.setPlate("ΑΒΕ-%04d".formatted(vehicles));
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

	private Customer customer(String lastName, String firstName, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setMobile(mobile);
		return customerRepository.save(customer);
	}

	private void owns(Vehicle vehicle, Customer customer, boolean primary, LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal(primary && toDate == null ? "60" : "40"));
		ownership.setPrimary(primary);
		ownership.setToDate(toDate);
		ownershipRepository.save(ownership);
	}

	// A yearly policy ending on the given day.
	private Policy policy(Vehicle vehicle, LocalDate endDate, String insuranceCompany) {
		return policy(vehicle, endDate.minusYears(1), endDate, insuranceCompany);
	}

	private Policy policy(Vehicle vehicle, LocalDate startDate, LocalDate endDate, String insuranceCompany) {
		policies++;
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("21%08d".formatted(policies));
		policy.setInsuranceCompany(insuranceCompany);
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
