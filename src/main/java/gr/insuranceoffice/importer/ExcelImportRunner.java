package gr.insuranceoffice.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import gr.insuranceoffice.importer.ImportResult.Counts;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Runs the one-off Excel migration (TASKS Task 4) against the configured
 * database, then lets the application exit. Exists only under the
 * {@code import} profile, so the normal startup never imports anything:
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=import \
 *     -Dspring-boot.run.arguments="--import.customers-file=ΠΕΛΑΤΕΣ.xlsx --import.archive-file=ΑΡΧΕΙΟ.xlsx"
 * </pre>
 *
 * A failed import throws, so the process exits with a non-zero code, and the
 * import's transaction leaves the database as it was.
 * <p>
 * Re-importing overwrites what the office has changed in the application
 * since (NOTES "Import risks"), so a database that already holds customers
 * or vehicles is refused unless {@code --import.allow-existing-data=true}.
 */
@Component
@Profile("import")
public class ExcelImportRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExcelImportRunner.class);

	private final ExcelImporterService importer;
	private final CustomerRepository customerRepository;
	private final VehicleRepository vehicleRepository;
	private final String customersFile;
	private final String archiveFile;
	private final boolean allowExistingData;

	public ExcelImportRunner(ExcelImporterService importer, CustomerRepository customerRepository,
			VehicleRepository vehicleRepository,
			@Value("${import.customers-file:}") String customersFile,
			@Value("${import.archive-file:}") String archiveFile,
			@Value("${import.allow-existing-data:false}") boolean allowExistingData) {
		this.importer = importer;
		this.customerRepository = customerRepository;
		this.vehicleRepository = vehicleRepository;
		this.customersFile = customersFile;
		this.archiveFile = archiveFile;
		this.allowExistingData = allowExistingData;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path customers = readable("import.customers-file", customersFile);
		Path archive = readable("import.archive-file", archiveFile);

		long existingCustomers = customerRepository.count();
		long existingVehicles = vehicleRepository.count();
		if ((existingCustomers > 0 || existingVehicles > 0) && !allowExistingData) {
			throw new IllegalStateException(("Η βάση έχει ήδη %d πελάτες και %d οχήματα. Μια νέα εισαγωγή "
					+ "αντικαθιστά τις αλλαγές που έγιναν στην εφαρμογή (NOTES «Import risks»). "
					+ "Για να συνεχίσετε, προσθέστε --import.allow-existing-data=true.")
					.formatted(existingCustomers, existingVehicles));
		}

		log.info("Εισαγωγή από {} και {}", customers.toAbsolutePath(), archive.toAbsolutePath());
		ImportResult result;
		try (InputStream customerStream = Files.newInputStream(customers);
				InputStream archiveStream = Files.newInputStream(archive)) {
			result = importer.importFiles(customerStream, archiveStream);
		} catch (IOException e) {
			throw new UncheckedIOException("Δεν διαβάστηκαν τα αρχεία εισαγωγής", e);
		} catch (ExcelImportException e) {
			// One line per problem, as the clerk will look them up in Excel.
			e.getErrors().forEach(error -> log.error("{}", error));
			throw e;
		}

		log.info("Η εισαγωγή ολοκληρώθηκε (νέα / ενημερωμένα / αφαιρεμένα):");
		log.info("  Διαμεσολαβητές {}", counts(result.intermediaries()));
		log.info("  Πελάτες        {}", counts(result.customers()));
		log.info("  Οχήματα        {}", counts(result.vehicles()));
		log.info("  Ιδιοκτησίες    {}", counts(result.ownerships()));
		log.info("  Συμβόλαια      {}", counts(result.policies()));
	}

	private static Path readable(String property, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Λείπει το --" + property + "=<αρχείο .xlsx>. Χρήση: "
					+ "--import.customers-file=<πελάτες.xlsx> --import.archive-file=<αρχείο.xlsx>");
		}
		Path path = Path.of(value);
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw new IllegalStateException("Δεν βρέθηκε ή δεν διαβάζεται το αρχείο " + path.toAbsolutePath()
					+ " (" + property + ")");
		}
		return path;
	}

	private static String counts(Counts counts) {
		return "%d / %d / %d".formatted(counts.created(), counts.updated(), counts.removed());
	}

}
