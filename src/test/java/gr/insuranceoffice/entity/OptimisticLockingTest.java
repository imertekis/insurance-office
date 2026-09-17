package gr.insuranceoffice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Two users edit the same record: ours is loaded first, theirs is saved while
 * ours is still open, then ours is saved. Ours must fail, and theirs must be
 * what the database keeps (SPEC §9).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OptimisticLockingTest {

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private PolicyRepository policyRepository;

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
	void newRecordsStartAtVersionZero() {
		Customer customer = customerRepository.save(customer());
		Vehicle vehicle = vehicleRepository.save(vehicle());
		Policy policy = policyRepository.save(policy(vehicle));

		assertThat(customer.getVersion()).isZero();
		assertThat(vehicle.getVersion()).isZero();
		assertThat(policy.getVersion()).isZero();
	}

	@Test
	void customerSaveFailsAfterAConcurrentChange() {
		Long id = customerRepository.save(customer()).getId();

		assertSecondSaveFails(customerRepository, id, theirs -> theirs.setMobile("6900000001"),
				ours -> ours.setMobile("6900000002"));

		Customer stored = customerRepository.findById(id).orElseThrow();
		assertThat(stored.getMobile()).isEqualTo("6900000001");
		assertThat(stored.getVersion()).isEqualTo(1);
	}

	@Test
	void vehicleSaveFailsAfterAConcurrentChange() {
		Long id = vehicleRepository.save(vehicle()).getId();

		assertSecondSaveFails(vehicleRepository, id, theirs -> theirs.setColor("Μαύρο"),
				ours -> ours.setColor("Κόκκινο"));

		assertThat(vehicleRepository.findById(id)).get().extracting(Vehicle::getColor).isEqualTo("Μαύρο");
	}

	@Test
	void policySaveFailsAfterAConcurrentChange() {
		Long id = policyRepository.save(policy(vehicleRepository.save(vehicle()))).getId();

		assertSecondSaveFails(policyRepository, id, theirs -> theirs.setPremium(new BigDecimal("150.00")),
				ours -> ours.setPremium(new BigDecimal("99.00")));

		assertThat(policyRepository.findById(id)).get().extracting(Policy::getPremium)
				.isEqualTo(new BigDecimal("150.00"));
	}

	// The same holds for a copy loaded earlier and saved later, as a form does.
	@Test
	void savingAnOutdatedCopyFails() {
		Customer ours = customerRepository.save(customer());
		Customer theirs = customerRepository.findById(ours.getId()).orElseThrow();
		theirs.setMobile("6900000001");
		customerRepository.save(theirs);

		ours.setMobile("6900000002");

		assertThatThrownBy(() -> customerRepository.save(ours))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(customerRepository.findById(ours.getId())).get().extracting(Customer::getMobile)
				.isEqualTo("6900000001");
	}

	private <T> void assertSecondSaveFails(JpaRepository<T, Long> repository, Long id, Consumer<T> theirChange,
			Consumer<T> ourChange) {
		TransactionTemplate ourTransaction = new TransactionTemplate(transactionManager);
		TransactionTemplate theirTransaction = new TransactionTemplate(transactionManager);
		theirTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertThatThrownBy(() -> ourTransaction.executeWithoutResult(status -> {
			T ours = repository.findById(id).orElseThrow();
			theirTransaction.executeWithoutResult(inner -> theirChange.accept(repository.findById(id).orElseThrow()));
			ourChange.accept(ours);
			repository.flush();
		}))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private static Customer customer() {
		Customer customer = new Customer();
		customer.setLastName("Αλεξίου");
		customer.setFirstName("Μαρία");
		customer.setTaxId("900000080");
		return customer;
	}

	private static Vehicle vehicle() {
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
		return vehicle;
	}

	private static Policy policy(Vehicle vehicle) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("2100000001");
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(LocalDate.of(2026, 3, 1));
		policy.setEndDate(LocalDate.of(2026, 9, 1));
		policy.setPremium(new BigDecimal("120.00"));
		return policy;
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
