package gr.insuranceoffice.importer;

import static gr.insuranceoffice.importer.ExcelFixtures.ALEXIOU_KONSTANTINOS_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.ARCHIVE_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.CUSTOMER_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.DIMITRIOU_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.INTERMEDIARY;
import static gr.insuranceoffice.importer.ExcelFixtures.STAVROU_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleArchive;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleCustomers;
import static gr.insuranceoffice.importer.ExcelFixtures.workbook;
import static gr.insuranceoffice.importer.ExcelImporterService.ARCHIVE_FILE;
import static gr.insuranceoffice.importer.ExcelImporterService.CUSTOMER_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Stubber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.OwnersSubmissionDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.OwnershipService;

/**
 * Task 18: a row a unique index refuses although the import checked it
 * against the file and the database is reported by row and column, in Greek,
 * not in PostgreSQL's words, and the import keeps nothing.
 * <p>
 * The first five are races with a clerk saving the same value in the
 * application, played out as in {@code UniqueViolationFormTest}: the
 * import's lookup is stubbed so that the clerk's row is committed, in a
 * transaction of its own, right after the lookup found nothing. The last is
 * the known limit of a re-import (NOTES «Import risks»).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UniqueViolationImportTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private ExcelImporterService importer;

	@Autowired
	private OwnershipService ownershipService;

	@MockitoSpyBean
	private CustomerRepository customerRepository;

	@MockitoSpyBean
	private VehicleRepository vehicleRepository;

	@MockitoSpyBean
	private PolicyRepository policyRepository;

	@MockitoSpyBean
	private IntermediaryRepository intermediaryRepository;

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
	void customerWithATaxIdAClerkJustSaved() {
		aClerkSaves(() -> customer("Άλλος", ALEXIOU_KONSTANTINOS_TAX_ID), Optional.empty())
				.when(customerRepository).findByTaxId(ALEXIOU_KONSTANTINOS_TAX_ID);

		assertRefused(new ImportError(CUSTOMER_FILE, 2, "Α.Φ.Μ.", "υπάρχει ήδη πελάτης με αυτό το ΑΦΜ στη βάση"));
		// Only the clerk's customer.
		assertThat(rowCounts()).containsExactly(0L, 1L, 0L, 0L, 0L);
	}

	@Test
	void vehicleWithAVinAClerkJustSaved() {
		aClerkSaves(() -> vehicle("ΚΑΒ-9999", "SYNTHVH0000000001"), Optional.empty())
				.when(vehicleRepository).findByVin("SYNTHVH0000000001");

		assertRefused(new ImportError(ARCHIVE_FILE, 2, "Αρ. Πλαισίου / VIN (E)",
				"υπάρχει ήδη όχημα με αυτό το VIN στη βάση"));
		assertThat(rowCounts()).containsExactly(0L, 0L, 1L, 0L, 0L);
	}

	@Test
	void vehicleWithAPlateAClerkJustSaved() {
		// The first archive row has «ΑΒΕ-1001»: the same plate in Latin letters.
		aClerkSaves(() -> vehicle("ABE-1001", "WVWZZZ1KZAW654321"), Optional.empty())
				.when(vehicleRepository).findByPlateNormalized("ABE1001");

		assertRefused(new ImportError(ARCHIVE_FILE, 2, "Αρ. Κυκλοφορίας (A)",
				"η πινακίδα ανήκει ήδη σε άλλο όχημα στη βάση"));
		assertThat(rowCounts()).containsExactly(0L, 0L, 1L, 0L, 0L);
	}

	@Test
	void policyWithANumberAClerkJustSaved() {
		aClerkSaves(() -> policy(vehicle("ΚΑΒ-9999", "WVWZZZ1KZAW654321"), "2100000001"), Optional.empty())
				.when(policyRepository).findByPolicyNumber("2100000001");

		assertRefused(new ImportError(ARCHIVE_FILE, 2, "Αρ. Συμβολαίου",
				"υπάρχει ήδη συμβόλαιο με αυτόν τον αριθμό στη βάση"));
		assertThat(rowCounts()).containsExactly(0L, 0L, 1L, 0L, 1L);
	}

	@Test
	void intermediaryWithANameAClerkJustSaved() {
		aClerkSaves(() -> intermediary(INTERMEDIARY), Optional.empty())
				.when(intermediaryRepository).findByFullName(INTERMEDIARY);

		assertRefused(new ImportError(ARCHIVE_FILE, 2, "Διαμεσολαβούν Πρόσωπο",
				"υπάρχει ήδη διαμεσολαβητής με αυτό το όνομα στη βάση"));
		assertThat(rowCounts()).containsExactly(1L, 0L, 0L, 0L, 0L);
	}

	// The limit of a re-import (NOTES «Import risks»): the form closed an
	// imported owner, whose row has no start, and the file still lists them.
	@Test
	void reimportOfAnOwnerTheOwnershipFormClosed() {
		importSample();
		Vehicle vehicle = vehicleRepository.findByVin("SYNTHVH0000000003").orElseThrow();
		Long stavrou = customerRepository.findByTaxId(STAVROU_TAX_ID).orElseThrow().getId();
		ownershipService.saveOwners(vehicle.getId(), new OwnersSubmissionDto(vehicle.getVersion(), TODAY, stavrou,
				List.of(stavrou), List.of("100")));
		assertThat(jdbcTemplate.queryForObject("""
				SELECT count(*) FROM ownership o JOIN customer c ON c.id = o.customer_id
				WHERE c.tax_id = ? AND o.from_date IS NULL AND o.to_date = ?
				""", Long.class, DIMITRIOU_TAX_ID, TODAY)).isOne();
		Map<String, List<Map<String, Object>>> before = snapshot();

		// The vehicle is the archive's third data row.
		assertRefused(new ImportError(ARCHIVE_FILE, 4, null, "ο πελάτης της στήλης «Α.Φ.Μ.» ή της «Α.Φ.Μ. "
				+ "Συνιδιοκτήτη» έχει ήδη σε αυτό το όχημα ιδιοκτησία χωρίς ημερομηνία έναρξης, που έκλεισε στη "
				+ "φόρμα ιδιοκτητών· η εισαγωγή δεν την ανοίγει ξανά"));
		// The rows the import had already updated are rolled back too.
		assertThat(snapshot()).isEqualTo(before);
	}

	/**
	 * The import's lookup, answered as it was just before a clerk committed
	 * the same value in the application: nothing there. The clerk's row is
	 * committed, in a transaction of its own, the first time the lookup runs.
	 */
	private Stubber aClerkSaves(Runnable save, Object lookupFinds) {
		AtomicBoolean saved = new AtomicBoolean();
		return doAnswer(invocation -> {
			if (saved.compareAndSet(false, true)) {
				TransactionTemplate clerk = new TransactionTemplate(transactionManager);
				clerk.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
				clerk.executeWithoutResult(status -> save.run());
			}
			return lookupFinds;
		});
	}

	// Exactly one error, in Greek: no PostgreSQL text.
	private void assertRefused(ImportError expected) {
		assertThatThrownBy(this::importSample)
				.isInstanceOfSatisfying(ExcelImportException.class,
						e -> assertThat(e.getErrors()).containsExactly(expected));
	}

	private void importSample() {
		importer.importFiles(workbook(CUSTOMER_HEADERS, sampleCustomers()), workbook(ARCHIVE_HEADERS, sampleArchive()));
	}

	/** Intermediaries, customers, vehicles, ownerships, policies. */
	private List<Long> rowCounts() {
		return List.of("intermediary", "customer", "vehicle", "ownership", "policy").stream()
				.map(table -> jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class))
				.toList();
	}

	// Every row that an import would write, with the versions it would raise.
	private Map<String, List<Map<String, Object>>> snapshot() {
		return Map.of(
				"intermediary", jdbcTemplate.queryForList("SELECT * FROM intermediary ORDER BY id"),
				"customer", jdbcTemplate.queryForList("SELECT id, tax_id, version FROM customer ORDER BY id"),
				"vehicle", jdbcTemplate.queryForList("SELECT id, vin, plate, version FROM vehicle ORDER BY id"),
				"ownership", jdbcTemplate.queryForList("SELECT * FROM ownership ORDER BY id"),
				"policy", jdbcTemplate.queryForList("SELECT id, policy_number, version FROM policy ORDER BY id"),
				"audit_log", jdbcTemplate.queryForList("SELECT id FROM audit_log ORDER BY id"));
	}

	private void customer(String lastName, String taxId) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setTaxId(taxId);
		customerRepository.save(customer);
	}

	private Vehicle vehicle(String plate, String vin) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Polo");
		vehicle.setFirstRegistration(LocalDate.of(2015, 3, 10));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Κόκκινο");
		vehicle.setEngineCc(1198);
		vehicle.setPowerKw(new BigDecimal("55"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private void policy(Vehicle vehicle, String policyNumber) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(TODAY.minusYears(3));
		policy.setEndDate(TODAY.minusYears(2));
		policy.setPremium(new BigDecimal("180.50"));
		policyRepository.save(policy);
	}

	private void intermediary(String fullName) {
		Intermediary intermediary = new Intermediary();
		intermediary.setFullName(fullName);
		intermediaryRepository.save(intermediary);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
