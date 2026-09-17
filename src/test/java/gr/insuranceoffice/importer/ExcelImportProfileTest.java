package gr.insuranceoffice.importer;

import static gr.insuranceoffice.importer.ExcelFixtures.ARCHIVE_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.CUSTOMER_HEADERS;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleArchive;
import static gr.insuranceoffice.importer.ExcelFixtures.sampleCustomers;
import static gr.insuranceoffice.importer.ExcelFixtures.workbook;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Starts the application with the import profile, as the documented command
 * does, and checks the files were imported during startup. Its own context,
 * so its own empty database.
 */
@SpringBootTest
@ActiveProfiles("import")
@Import(TestcontainersConfiguration.class)
class ExcelImportProfileTest {

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	private PolicyRepository policyRepository;

	// Runs before the context starts, so the files exist when the runner reads them.
	@DynamicPropertySource
	static void importFiles(DynamicPropertyRegistry registry) throws IOException {
		Path dir = Files.createTempDirectory("excel-import-profile");
		dir.toFile().deleteOnExit();
		Path customers = write(dir.resolve("customers.xlsx"), workbook(CUSTOMER_HEADERS, sampleCustomers()));
		Path archive = write(dir.resolve("archive.xlsx"), workbook(ARCHIVE_HEADERS, sampleArchive()));
		registry.add("import.customers-file", customers::toString);
		registry.add("import.archive-file", archive::toString);
	}

	@Test
	void importsTheFilesDuringStartup() {
		assertThat(customerRepository.count()).isEqualTo(8);
		assertThat(vehicleRepository.count()).isEqualTo(8);
		assertThat(ownershipRepository.count()).isEqualTo(9);
		assertThat(policyRepository.count()).isEqualTo(8);
	}

	private static Path write(Path file, InputStream workbook) throws IOException {
		try (workbook) {
			Files.copy(workbook, file);
		}
		file.toFile().deleteOnExit();
		return file;
	}

}
