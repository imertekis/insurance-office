package gr.insuranceoffice.importer;

import static gr.insuranceoffice.importer.ExcelFixtures.ALEXIOU_KONSTANTINOS_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.ALEXIOU_MARIA_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.ARCHIVE_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.CUSTOMER_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.INTERMEDIARY;
import static gr.insuranceoffice.importer.ExcelFixtures.OIKONOMOU_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.UNKNOWN_TAX_ID;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleArchive;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleCustomers;
import static gr.insuranceoffice.importer.ExcelFixtures.workbook;
import static gr.insuranceoffice.importer.ExcelImporterService.ARCHIVE_FILE;
import static gr.insuranceoffice.importer.ExcelImporterService.CUSTOMER_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.importer.ImportResult.Counts;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.SearchService;

/**
 * Imports synthetic workbooks shaped like the office's files into PostgreSQL.
 * Not {@code @Transactional}: every import commits, so that a second run has
 * to find the first run's rows in the database, as it would in real use.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExcelImporterServiceTest {

	private static final String VIN = "Αρ. Πλαισίου / VIN (E)";

	private static final String PLATE = "Αρ. Κυκλοφορίας (A)";

	@Autowired
	private ExcelImporterService importer;

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
	private IntermediaryRepository intermediaryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void startEmpty() {
		truncateImportedTables();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateImportedTables();
	}

	@Test
	void importsTheExpectedNumberOfRows() {
		ImportResult result = importSample();

		// DATA_MODEL "Αναμενόμενο αποτέλεσμα import": 9 ownerships = 7×100% + 2×50%.
		assertThat(result).isEqualTo(new ImportResult(created(1), created(8), created(8), created(9), created(8)));
		assertRowCounts(1, 8, 8, 9, 8);
	}

	@Test
	void mapsCustomerColumns() {
		importSample();

		assertThat(customerRepository.findByTaxId(ALEXIOU_KONSTANTINOS_TAX_ID)).get()
				.extracting(Customer::getLastName, Customer::getFirstName, Customer::getFatherName,
						Customer::getStreet, Customer::getCity, Customer::getPostalCode, Customer::getBirthDate,
						Customer::getTaxOffice, Customer::getMobile, Customer::getPhone, Customer::getEmail,
						Customer::getLicenseDate)
				.containsExactly("Αλεξίου", "Κωνσταντίνος", "Γεώργιος", "Οδός Δοκιμής 1", "Δοκιμούπολη", "99000",
						LocalDate.of(1980, 4, 15), "Δοκιμούπολης", "6900000001", "2990000000", "test@example.com",
						LocalDate.of(1999, 5, 20));
	}

	@Test
	void mapsVehicleColumnsIncludingTheLicenceAddress() {
		importSample();

		Vehicle vehicle = vehicleRepository.findByVin("SYNTHVH0000000001").orElseThrow();
		assertThat(vehicle)
				.extracting(Vehicle::getPlate, Vehicle::getBrand, Vehicle::getModel, Vehicle::getFirstRegistration,
						Vehicle::getLicenseIssueDate, Vehicle::getCategory, Vehicle::getUsageType, Vehicle::getColor,
						Vehicle::getSeats, Vehicle::getEngineCc, Vehicle::getFuelType, Vehicle::getEngineNumber,
						Vehicle::getCo2, Vehicle::getEmissionStandard, Vehicle::getWeightKg)
				.containsExactly("ΑΒΕ1001", "Volkswagen", "Golf", LocalDate.of(2012, 5, 14),
						LocalDate.of(2021, 11, 22), "M1", UsageType.ΕΙΧ, "Λευκό", (short) 5, 1598, FuelType.ΒΕΝΖΙΝΗ,
						"ENG0001", 120, "Euro 6", 1250);
		assertThat(vehicle.getPowerKw()).isEqualByComparingTo("81");
		// C.1.3 is a snapshot kept on the vehicle, not the owner's current address.
		assertThat(vehicle)
				.extracting(Vehicle::getLicenseStreet, Vehicle::getLicenseCity, Vehicle::getLicensePostalCode)
				.containsExactly("Οδός Άδειας 10", "Δοκιμοχώρι", "99100");
		// Normalized by Vehicle's callback, not by the importer.
		assertThat(vehicle.getPlateNormalized()).isEqualTo("ABE1001");
	}

	@Test
	void mapsPolicyColumnsWithoutAssumingADuration() {
		importSample();

		transactionTemplate.executeWithoutResult(status -> {
			Policy policy = policyRepository.findByPolicyNumber("2100000001").orElseThrow();
			assertThat(policy.getVehicle().getVin()).isEqualTo("SYNTHVH0000000001");
			assertThat(policy.getIntermediary().getFullName()).isEqualTo(INTERMEDIARY);
			assertThat(policy.getInsuranceCompany()).isEqualTo("Δοκιμαστική Ασφαλιστική");
			assertThat(policy.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
			assertThat(policy.getEndDate()).isEqualTo(LocalDate.of(2027, 3, 1));
			// "180,00 €" becomes a number with two decimals.
			assertThat(policy.getPremium()).isEqualTo(new BigDecimal("180.00"));
		});
		assertThat(policyRepository.findByPolicyNumber("2100000006")).get()
				.extracting(Policy::getStartDate, Policy::getEndDate)
				.containsExactly(LocalDate.of(2026, 3, 26), LocalDate.of(2026, 9, 26));
	}

	@Test
	void convertsSurchargeTextToAFlagAndAType() {
		importSample();

		// «ΝΑΙ (Ν.Ο.Δ.)»
		assertThat(policyRepository.findByPolicyNumber("2100000002")).get()
				.extracting(Policy::isSurcharge, Policy::getSurchargeType)
				.containsExactly(true, SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ);
		// «ΝΑΙ (Ε.Η.)»
		assertThat(policyRepository.findByPolicyNumber("2100000001")).get()
				.extracting(Policy::isSurcharge, Policy::getSurchargeType)
				.containsExactly(true, SurchargeType.ΗΛΙΚΙΑΣ);
		// «ΟΧΙ»
		assertThat(policyRepository.findByPolicyNumber("2100000003")).get()
				.extracting(Policy::isSurcharge, Policy::getSurchargeType)
				.containsExactly(false, null);
	}

	@Test
	void storesZeroEngineCcOfAnElectricVehicleAsNull() {
		importSample();

		Vehicle electric = vehicleRepository.findByVin("SYNTHVH0000000008").orElseThrow();
		assertThat(electric.getEngineCc()).isNull();
		// Only engine capacity uses 0 for "none"; zero emissions is a real value.
		assertThat(electric.getCo2()).isZero();
	}

	@Test
	void savesACustomerWhoHasOnlyANameAndATaxId() {
		importSample();

		Customer maria = customerRepository.findByTaxId(ALEXIOU_MARIA_TAX_ID).orElseThrow();
		assertThat(maria.getLastName()).isEqualTo("Αλεξίου");
		assertThat(maria.getFirstName()).isEqualTo("Μαρία");
		assertThat(maria)
				.extracting(Customer::getMobile, Customer::getPhone, Customer::getEmail, Customer::getFatherName,
						Customer::getStreet, Customer::getCity, Customer::getPostalCode, Customer::getBirthDate,
						Customer::getTaxOffice, Customer::getLicenseDate)
				.containsOnlyNulls();
	}

	@Test
	void linksCoOwnersByTaxIdWithExactlyOnePrimary() {
		importSample();

		transactionTemplate.executeWithoutResult(status -> {
			Vehicle coOwned = vehicleRepository.findByVin("SYNTHVH0000000004").orElseThrow();
			assertThat(ownershipRepository.findByVehicle(coOwned))
					.extracting(ownership -> ownership.getCustomer().getTaxId(), Ownership::getPercentage,
							Ownership::isPrimary)
					.containsExactlyInAnyOrder(
							tuple(OIKONOMOU_TAX_ID, new BigDecimal("50.00"), true),
							tuple(ALEXIOU_MARIA_TAX_ID, new BigDecimal("50.00"), false));
		});
	}

	@Test
	void runningTwiceUpdatesInsteadOfDuplicating() {
		importSample();

		ImportResult second = importSample();

		assertThat(second).isEqualTo(new ImportResult(updated(1), updated(8), updated(8), updated(9), updated(8)));
		assertRowCounts(1, 8, 8, 9, 8);
	}

	@Test
	void rerunOverwritesValuesMatchedByNaturalKey() {
		importSample();
		List<Map<String, Object>> customers = sampleCustomers();
		customers.get(7).put("Κινητό Τηλέφωνο", "6900000099");
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(0).put("Αρ. Κυκλοφορίας (A)", "ΑΒΕ-9999");
		archive.get(0).put("Πληρωτέα Μικτά Ασφάλιστρα", "199,90 €");

		importFiles(customers, archive);

		assertRowCounts(1, 8, 8, 9, 8);
		assertThat(customerRepository.findByTaxId(ALEXIOU_MARIA_TAX_ID)).get()
				.extracting(Customer::getMobile)
				.isEqualTo("6900000099");
		assertThat(vehicleRepository.findByVin("SYNTHVH0000000001")).get()
				.extracting(Vehicle::getPlate, Vehicle::getPlateNormalized)
				.containsExactly("ΑΒΕ9999", "ABE9999");
		assertThat(policyRepository.findByPolicyNumber("2100000001")).get()
				.extracting(Policy::getPremium)
				.isEqualTo(new BigDecimal("199.90"));
	}

	// Task 14, REVIEW-05 finding 3: a plate is stored without dashes or spaces
	// whichever way in, and the importer does not strip it itself; Vehicle's
	// setter does, so this path cannot store one differently from the form.
	@Test
	void storesAPlateWithoutItsDashOrSpaceAndKeepsItSoOnARerun() {
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(0).put("Αρ. Κυκλοφορίας (A)", "ΑΒΕ-1001");
		archive.get(1).put("Αρ. Κυκλοφορίας (A)", "TST 1002");
		// What a spreadsheet's autocorrect makes of a hyphen.
		archive.get(2).put("Αρ. Κυκλοφορίας (A)", "TST\u20131003");

		importFiles(sampleCustomers(), archive);

		assertThat(vehicleRepository.findByVin("SYNTHVH0000000001")).get().extracting(Vehicle::getPlate)
				.isEqualTo("ΑΒΕ1001");
		assertThat(vehicleRepository.findByVin("SYNTHVH0000000002")).get().extracting(Vehicle::getPlate)
				.isEqualTo("TST1002");
		assertThat(vehicleRepository.findByVin("SYNTHVH0000000003")).get().extracting(Vehicle::getPlate)
				.isEqualTo("TST1003");
		assertThat(storedPlates()).hasSize(8).allMatch(plate -> plate.matches("[^\\s\\p{Pd}]+"));

		// The same file again finds the vehicles by VIN and sets the same
		// plates: still without dashes, and no change for the audit log to record.
		long updatesBefore = vehicleUpdatesLogged();
		importFiles(sampleCustomers(), archive);

		assertThat(storedPlates()).hasSize(8).allMatch(plate -> plate.matches("[^\\s\\p{Pd}]+"));
		assertThat(vehicleUpdatesLogged()).isEqualTo(updatesBefore);
	}

	// Task 17: in capitals whichever way in. Before, a VIN imported in small
	// letters was stored so, and the VIN search, which upper-cases what is
	// typed and compares exactly, could not find it.
	@Test
	void storesPlateAndVinInCapitalsSoTheVinSearchFindsThem() {
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(0).put(VIN, "synthvh0000000001");
		archive.get(0).put(PLATE, "άβε-1001");
		archive.get(1).put(PLATE, "tst 1002");

		importFiles(sampleCustomers(), archive);

		Vehicle stored = vehicleRepository.findByVin("SYNTHVH0000000001").orElseThrow();
		assertThat(stored.getPlate()).isEqualTo("ΑΒΕ1001");
		assertThat(vehicleRepository.findByVin("SYNTHVH0000000002")).get().extracting(Vehicle::getPlate)
				.isEqualTo("TST1002");
		assertThat(searchService.search("synthvh0000000001").vehicles()).extracting(VehicleHit::id)
				.containsExactly(stored.getId());

		// The same file again finds its vehicle by VIN, whatever the case.
		importFiles(sampleCustomers(), archive);
		assertThat(vehicleRepository.count()).isEqualTo(8);
	}

	@Test
	void refusesAVinOfTheWrongShapeAndAPlateRepeatedInTheFile() {
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(1).put(VIN, "SYNTHVH00000000I2");
		// The same plate as row 4, in Latin small letters: one plate all the same.
		archive.get(2).put(PLATE, "ΤΚΤ-7777");
		archive.get(3).put(PLATE, "tkt7777");

		assertThatThrownBy(() -> importFiles(sampleCustomers(), archive))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row, ImportError::column, ImportError::message)
						.containsExactly(
								tuple(ARCHIVE_FILE, 3, VIN, "το VIN έχει 17 χαρακτήρες, χωρίς τα γράμματα I, O και Q"),
								tuple(ARCHIVE_FILE, 5, PLATE, "η ίδια πινακίδα υπάρχει σε προηγούμενη γραμμή")));
		assertRowCounts(0, 0, 0, 0, 0);
	}

	// Instead of the database's unique-index error.
	@Test
	void refusesAPlateAnotherVehicleAlreadyHas() {
		importSample();
		// The first vehicle stays in the database but is not in this file.
		List<Map<String, Object>> archive = new ArrayList<>(sampleArchive().subList(1, 8));
		archive.get(0).put(PLATE, "αβε1001");

		assertThatThrownBy(() -> importFiles(sampleCustomers(), archive))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row, ImportError::column, ImportError::message)
						.containsExactly(tuple(ARCHIVE_FILE, 2, PLATE,
								"η πινακίδα ανήκει ήδη σε άλλο όχημα (VIN SYNTHVH0000000001)")));
		assertThat(vehicleRepository.findByVin("SYNTHVH0000000002")).get().extracting(Vehicle::getPlate)
				.isEqualTo("TST1002");
	}

	@Test
	void rerunRemovesAnOwnerNoLongerInTheFile() {
		importSample();
		List<Map<String, Object>> archive = sampleArchive();
		Map<String, Object> formerlyCoOwned = archive.get(3);
		formerlyCoOwned.put("Ποσοστό Ιδιοκτησίας (Κύριος %)", "100%");
		formerlyCoOwned.put("Συνιδιοκτήτης (Επώνυμο - Όνομα)", "-");
		formerlyCoOwned.put("Α.Φ.Μ. Συνιδιοκτήτη", "-");
		formerlyCoOwned.put("Ποσοστό Ιδιοκτησίας (Συνιδιοκτήτης %)", "-");

		ImportResult result = importFiles(sampleCustomers(), archive);

		assertThat(result.ownerships()).isEqualTo(new Counts(0, 8, 1));
		assertThat(ownershipRepository.count()).isEqualTo(8);
		// Only the link goes; the customer stays.
		assertThat(customerRepository.findByTaxId(ALEXIOU_MARIA_TAX_ID)).isPresent();
	}

	@Test
	void reportsEveryBadRowAndKeepsNothing() {
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(1).put("Λήξη Ασφάλειας", "31/02/2027");
		archive.get(4).put("Α.Φ.Μ.", UNKNOWN_TAX_ID);
		archive.get(6).put("Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)", "ΙΣΩΣ");

		assertThatThrownBy(() -> importFiles(sampleCustomers(), archive))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row, ImportError::column)
						.containsExactly(
								tuple(ARCHIVE_FILE, 3, "Λήξη Ασφάλειας"),
								tuple(ARCHIVE_FILE, 6, "Α.Φ.Μ."),
								tuple(ARCHIVE_FILE, 8, "Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)")));
		// All or nothing: the valid rows around them were rolled back as well.
		assertRowCounts(0, 0, 0, 0, 0);
	}

	@Test
	void refusesOwnershipSharesThatDoNotSumToAHundred() {
		List<Map<String, Object>> archive = sampleArchive();
		// Co-owned 50/40, and a sole owner with 80%.
		archive.get(3).put("Ποσοστό Ιδιοκτησίας (Συνιδιοκτήτης %)", "40%");
		archive.get(5).put("Ποσοστό Ιδιοκτησίας (Κύριος %)", "80%");

		assertThatThrownBy(() -> importFiles(sampleCustomers(), archive))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row, ImportError::column, ImportError::message)
						.containsExactly(
								tuple(ARCHIVE_FILE, 5, "Ποσοστό Ιδιοκτησίας (Κύριος %)",
										"τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν 90% αντί για 100%"),
								tuple(ARCHIVE_FILE, 7, "Ποσοστό Ιδιοκτησίας (Κύριος %)",
										"τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν 80% αντί για 100%")));
		assertRowCounts(0, 0, 0, 0, 0);
	}

	@Test
	void reportsARowTheDatabaseRefusesWithItsRowNumber() {
		List<Map<String, Object>> archive = sampleArchive();
		// End before start is not re-checked by the importer; the database refuses it.
		archive.get(2).put("Λήξη Ασφάλειας", "01/01/2026");

		assertThatThrownBy(() -> importFiles(sampleCustomers(), archive))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.singleElement()
						.satisfies(error -> {
							assertThat(error.file()).isEqualTo(ARCHIVE_FILE);
							assertThat(error.row()).isEqualTo(4);
							assertThat(error.message()).contains("policy_end_after_start");
						}));
		assertRowCounts(0, 0, 0, 0, 0);
	}

	@Test
	void reportsAMissingColumnBeforeReadingAnyRow() {
		List<String> headers = new ArrayList<>(ARCHIVE_HEADERS);
		headers.remove("Αρ. Πλαισίου / VIN (E)");

		assertThatThrownBy(() -> importer.importFiles(workbook(CUSTOMER_HEADERS, sampleCustomers()),
				workbook(headers, sampleArchive())))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row, ImportError::column)
						.containsExactly(tuple(ARCHIVE_FILE, 1, "Αρ. Πλαισίου / VIN (E)")));
		assertRowCounts(0, 0, 0, 0, 0);
	}

	@Test
	void reportsAFileThatIsNotAWorkbook() {
		InputStream notExcel = new ByteArrayInputStream("Επώνυμο;Όνομα;Α.Φ.Μ.".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> importer.importFiles(notExcel, workbook(ARCHIVE_HEADERS, sampleArchive())))
				.isInstanceOfSatisfying(ExcelImportException.class, e -> assertThat(e.getErrors())
						.extracting(ImportError::file, ImportError::row)
						.containsExactly(tuple(CUSTOMER_FILE, 0)));
	}

	@Test
	void readsDatesAndNumbersRetypedAsRealExcelCells() {
		// Retyping a value in Excel turns the text into a date or number cell.
		List<Map<String, Object>> customers = sampleCustomers();
		customers.get(0).put("Ημερομηνία Γέννησης", LocalDate.of(1981, 6, 30));
		List<Map<String, Object>> archive = sampleArchive();
		archive.get(0).put("Λήξη Ασφάλειας", LocalDate.of(2027, 2, 28));
		archive.get(0).put("Κυβικά (P.1)", 1395);
		archive.get(0).put("Ισχύς kW (P.2)", 81.5);
		archive.get(0).put("Πληρωτέα Μικτά Ασφάλιστρα", 210);

		importFiles(customers, archive);

		assertThat(customerRepository.findByTaxId(ALEXIOU_KONSTANTINOS_TAX_ID)).get()
				.extracting(Customer::getBirthDate)
				.isEqualTo(LocalDate.of(1981, 6, 30));
		Vehicle vehicle = vehicleRepository.findByVin("SYNTHVH0000000001").orElseThrow();
		assertThat(vehicle.getEngineCc()).isEqualTo(1395);
		assertThat(vehicle.getPowerKw()).isEqualByComparingTo("81.5");
		assertThat(policyRepository.findByPolicyNumber("2100000001")).get()
				.extracting(Policy::getEndDate, Policy::getPremium)
				.containsExactly(LocalDate.of(2027, 2, 28), new BigDecimal("210.00"));
	}

	private ImportResult importSample() {
		return importFiles(sampleCustomers(), sampleArchive());
	}

	private ImportResult importFiles(List<Map<String, Object>> customers, List<Map<String, Object>> archive) {
		return importer.importFiles(workbook(CUSTOMER_HEADERS, customers), workbook(ARCHIVE_HEADERS, archive));
	}

	private void assertRowCounts(long intermediaries, long customers, long vehicles, long ownerships,
			long policies) {
		assertThat(List.of(intermediaryRepository.count(), customerRepository.count(), vehicleRepository.count(),
				ownershipRepository.count(), policyRepository.count()))
				.as("intermediaries, customers, vehicles, ownerships, policies")
				.containsExactly(intermediaries, customers, vehicles, ownerships, policies);
	}

	private List<String> storedPlates() {
		return jdbcTemplate.queryForList("SELECT plate FROM vehicle ORDER BY vin", String.class);
	}

	private long vehicleUpdatesLogged() {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_log WHERE entity_type = 'Vehicle' AND action = 'UPDATE'", Long.class);
	}

	private void truncateImportedTables() {
		jdbcTemplate.execute("TRUNCATE ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

	private static Counts created(int count) {
		return new Counts(count, 0, 0);
	}

	private static Counts updated(int count) {
		return new Counts(0, count, 0);
	}

}
