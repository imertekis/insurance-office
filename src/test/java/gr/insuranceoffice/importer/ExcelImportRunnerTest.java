package gr.insuranceoffice.importer;

import static gr.insuranceoffice.importer.ExcelFixtures.ARCHIVE_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.CUSTOMER_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleArchive;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleCustomers;
import static gr.insuranceoffice.importer.ExcelFixtures.workbook;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * The runner's own checks, called directly. That it runs at startup under
 * the import profile is in {@link ExcelImportProfileTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ExcelImportRunnerTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private ExcelImporterService importer;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@TempDir
	private Path dir;

	private Path customers;

	private Path archive;

	@BeforeEach
	void startEmpty() throws IOException {
		truncateTables();
		customers = write("customers.xlsx", workbook(CUSTOMER_HEADERS, sampleCustomers()));
		archive = write("archive.xlsx", workbook(ARCHIVE_HEADERS, sampleArchive()));
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void isNotPartOfTheNormalStartup() {
		assertThat(context.getBeansOfType(ExcelImportRunner.class)).isEmpty();
	}

	@Test
	void importsBothFilesIntoAnEmptyDatabase() {
		runner(customers, archive, false).run(null);

		assertThat(customerRepository.count()).isEqualTo(8);
		assertThat(vehicleRepository.count()).isEqualTo(8);
		assertThat(policyRepository.count()).isEqualTo(8);
	}

	// Task 23a: a successful import has more to say than counts. One line
	// per value imported outside its list, as the errors are printed.
	@Test
	void printsEveryValueImportedOutsideItsList(CapturedOutput output) throws IOException {
		List<Map<String, Object>> rows = sampleArchive();
		rows.get(4).put("Χρώμα (R)", "ΛΑΔΙ");
		rows.get(6).put("Κατηγορία (J)", "Ι.Χ.");
		Files.delete(archive);
		archive = write("archive.xlsx", workbook(ARCHIVE_HEADERS, rows));

		runner(customers, archive, false).run(null);

		assertThat(vehicleRepository.count()).isEqualTo(8);
		assertThat(output.getOut()).contains(
				"2 τιμές εκτός λίστας εισήχθησαν όπως είναι· διορθώστε τις στην εφαρμογή:",
				"Αρχείο οχημάτων και συμβολαίων, γραμμή 6, στήλη «Χρώμα (R)»: «ΛΑΔΙ» εκτός λίστας· εισήχθη όπως είναι",
				"Αρχείο οχημάτων και συμβολαίων, γραμμή 8, στήλη «Κατηγορία (J)»: «Ι.Χ.» εκτός λίστας· εισήχθη όπως είναι");
	}

	@Test
	void printsNoWarningForValuesOfTheLists(CapturedOutput output) {
		runner(customers, archive, false).run(null);

		assertThat(output.getOut()).contains("Η εισαγωγή ολοκληρώθηκε").doesNotContain("εκτός λίστας");
	}

	// NOTES "Import risks": a re-import overwrites what the office changed.
	@Test
	void refusesADatabaseThatAlreadyHasDataUnlessAllowed() {
		runner(customers, archive, false).run(null);
		jdbcTemplate.update("UPDATE customer SET city = 'Άλλαξε στην εφαρμογή'");

		assertThatThrownBy(() -> runner(customers, archive, false).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("8 πελάτες και 8 οχήματα")
				.hasMessageContaining("--import.allow-existing-data=true");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer WHERE city = 'Άλλαξε στην εφαρμογή'",
				Long.class)).isEqualTo(8);

		runner(customers, archive, true).run(null);

		assertThat(customerRepository.count()).isEqualTo(8);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer WHERE city = 'Άλλαξε στην εφαρμογή'",
				Long.class)).isZero();
	}

	@Test
	void explainsAMissingFileArgument() {
		assertThatThrownBy(() -> runner(null, archive, false).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--import.customers-file=");
		assertThatThrownBy(() -> runner(customers, Path.of(" "), false).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--import.archive-file=");
	}

	@Test
	void namesAFileThatDoesNotExist() {
		Path missing = dir.resolve("λείπει.xlsx");

		assertThatThrownBy(() -> runner(customers, missing, false).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(missing.toAbsolutePath().toString());
		assertThat(customerRepository.count()).isZero();
	}

	// The failure propagates, so the process exits non-zero, and the import's
	// transaction keeps nothing.
	@Test
	void failsWithTheImportErrorsAndKeepsNothing() {
		// Swapped: each file has the other's columns.
		assertThatThrownBy(() -> runner(archive, customers, false).run(null))
				.isInstanceOf(ExcelImportException.class);

		assertThat(customerRepository.count()).isZero();
		assertThat(vehicleRepository.count()).isZero();
	}

	private ExcelImportRunner runner(Path customersFile, Path archiveFile, boolean allowExistingData) {
		return new ExcelImportRunner(importer, customerRepository, vehicleRepository,
				customersFile == null ? "" : customersFile.toString(), archiveFile.toString(), allowExistingData);
	}

	private Path write(String name, InputStream workbook) throws IOException {
		Path file = dir.resolve(name);
		try (workbook) {
			Files.copy(workbook, file);
		}
		return file;
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
