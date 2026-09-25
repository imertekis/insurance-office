package gr.insuranceoffice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.UniqueConstraint;

/**
 * Proves the V3 schema and the JPA mappings agree: Hibernate runs with
 * {@code ddl-auto: validate}, so the context only starts if every entity
 * matches the tables Flyway created.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FullSchemaTest {

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private IntermediaryRepository intermediaryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void mapsOwnershipAndPolicyRelationsInBothDirections() {
		Intermediary intermediary = intermediaryRepository.save(intermediary("Παπαδόπουλος Γιώργος"));
		Vehicle vehicle = vehicleRepository.save(vehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234"));
		Customer first = customerRepository.save(customer("Αλεξίου", "Μαρία"));
		Customer second = customerRepository.save(customer("Αλεξίου", "Κωνσταντίνος"));

		ownershipRepository.save(ownership(vehicle, first, new BigDecimal("50.00"), true));
		ownershipRepository.save(ownership(vehicle, second, new BigDecimal("50.00"), false));
		policyRepository.save(policy(vehicle, intermediary, "2100000001"));

		entityManager.flush();
		entityManager.clear();

		Vehicle reloaded = vehicleRepository.findById(vehicle.getId()).orElseThrow();
		assertThat(reloaded.getOwnerships()).hasSize(2);
		assertThat(reloaded.getOwnerships()).extracting(Ownership::getPercentage)
				.containsExactlyInAnyOrder(new BigDecimal("50.00"), new BigDecimal("50.00"));
		assertThat(reloaded.getOwnerships()).filteredOn(Ownership::isPrimary).hasSize(1);
		assertThat(reloaded.getPolicies()).hasSize(1);

		Policy reloadedPolicy = reloaded.getPolicies().get(0);
		assertThat(reloadedPolicy.getVehicle().getId()).isEqualTo(vehicle.getId());
		assertThat(reloadedPolicy.getIntermediary().getFullName()).isEqualTo("Παπαδόπουλος Γιώργος");

		// The other direction: customer -> ownership -> vehicle (SPEC §7.3).
		Customer reloadedCustomer = customerRepository.findById(first.getId()).orElseThrow();
		assertThat(reloadedCustomer.getOwnerships()).hasSize(1);
		assertThat(reloadedCustomer.getOwnerships().get(0).getVehicle().getPlate()).isEqualTo("ΑΒΕ1234");
	}

	@Test
	void fillsPlateNormalizedInJavaAndSearchNormalizedInTheDatabase() {
		Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234"));

		// Greek plate letters become their Latin look-alikes (CLAUDE.md §5).
		assertThat(vehicle.getPlateNormalized()).isEqualTo("ABE1234");
		assertThat(vehicle.getSearchNormalized())
				.isEqualTo("ABE1234 WVWZZZ1KZAW123456 VOLKSWAGEN GOLF");
	}

	@Test
	void storesNullEngineCcForAnElectricVehicle() {
		Vehicle electric = vehicle("5YJ3E1EA7JF000001", "ΝΖΑ-8812");
		electric.setBrand("Tesla");
		electric.setModel("Model 3");
		electric.setFuelType(FuelType.ΗΛΕΚΤΡΙΣΜΟΣ);
		electric.setEngineCc(null);

		Vehicle saved = vehicleRepository.saveAndFlush(electric);
		entityManager.clear();

		assertThat(vehicleRepository.findById(saved.getId()).orElseThrow().getEngineCc()).isNull();
	}

	@Test
	void persistsGreekEnumValuesUnchanged() {
		Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234"));
		Policy policy = policy(vehicle, null, "2100000002");
		policy.setSurcharge(true);
		policy.setSurchargeType(SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ);
		policyRepository.saveAndFlush(policy);

		assertThat(columnValue("SELECT fuel_type FROM vehicle WHERE id = " + vehicle.getId()))
				.isEqualTo("ΒΕΝΖΙΝΗ");
		assertThat(columnValue("SELECT usage_type FROM vehicle WHERE id = " + vehicle.getId()))
				.isEqualTo("ΕΙΧ");
		assertThat(columnValue("SELECT surcharge_type FROM policy WHERE id = " + policy.getId()))
				.isEqualTo("ΝΕΟΣ_ΟΔΗΓΟΣ");
	}

	@Test
	void keepsOptionalColumnsNullable() {
		assertThat(isNullable("customer", "tax_id")).isEqualTo("YES");
		assertThat(isNullable("customer", "mobile")).isEqualTo("YES");
		assertThat(isNullable("vehicle", "engine_cc")).isEqualTo("YES");
	}

	@Test
	void createsUniqueIndexesWithoutSoftDeleteFilters() {
		List<String> indexNames = jdbcTemplate.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

		assertThat(indexNames).contains(
				"idx_customer_tax_id",
				"idx_vehicle_vin",
				"idx_vehicle_plate",
				"idx_policy_number",
				"idx_intermediary_full_name",
				"idx_app_user_username",
				"idx_ownership_vehicle_customer_from");

		// Hard delete only (DECISIONS §4): no partial indexes, no deleted_at.
		List<String> definitions = jdbcTemplate.queryForList(
				"SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'", String.class);
		assertThat(definitions).noneMatch(definition -> definition.contains("deleted_at"));

		List<String> softDeleteColumns = jdbcTemplate.queryForList("""
				SELECT table_name || '.' || column_name
				FROM information_schema.columns
				WHERE table_schema = 'public' AND column_name = 'deleted_at'
				""", String.class);
		assertThat(softDeleteColumns).isEmpty();
	}

	// Task 18: a save that breaks a unique index shows a Greek message, not
	// an error page. A new index fails here until UniqueConstraint lists it.
	@Test
	void givesEveryUniqueIndexAMessage() {
		List<String> uniqueIndexes = jdbcTemplate.queryForList("""
				SELECT index_class.relname
				FROM pg_index i
				JOIN pg_class index_class ON index_class.oid = i.indexrelid
				JOIN pg_namespace n ON n.oid = index_class.relnamespace
				WHERE n.nspname = 'public' AND i.indisunique AND NOT i.indisprimary
				""", String.class);

		List<String> listed = new ArrayList<>(Arrays.stream(UniqueConstraint.values())
				.map(UniqueConstraint::indexName).toList());
		// Out of scope: accounts are made by the create-user profile, which
		// looks the user up first.
		listed.add("idx_app_user_username");
		assertThat(uniqueIndexes).containsExactlyInAnyOrderElementsOf(listed);
	}

	private String isNullable(String table, String column) {
		return jdbcTemplate.queryForObject("""
				SELECT is_nullable FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
				""", String.class, table, column);
	}

	private String columnValue(String sql) {
		return jdbcTemplate.queryForObject(sql, String.class);
	}

	private static Vehicle vehicle(String vin, String plate) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2015, 3, 12));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("ΛΕΥΚΟ");
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81.00"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicle;
	}

	private static Customer customer(String lastName, String firstName) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		return customer;
	}

	private static Intermediary intermediary(String fullName) {
		Intermediary intermediary = new Intermediary();
		intermediary.setFullName(fullName);
		return intermediary;
	}

	private static Ownership ownership(Vehicle vehicle, Customer customer, BigDecimal percentage,
			boolean primary) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(percentage);
		ownership.setPrimary(primary);
		return ownership;
	}

	private static Policy policy(Vehicle vehicle, Intermediary intermediary, String policyNumber) {
		Policy policy = new Policy();
		policy.setPolicyNumber(policyNumber);
		policy.setVehicle(vehicle);
		policy.setIntermediary(intermediary);
		policy.setInsuranceCompany("ΑΣΦΑΛΙΣΤΙΚΗ Α.Ε.");
		policy.setStartDate(LocalDate.of(2025, 1, 1));
		// Six-month policy: no duration is assumed anywhere in the code.
		policy.setEndDate(LocalDate.of(2025, 7, 1));
		policy.setPremium(new BigDecimal("180.00"));
		return policy;
	}

}
