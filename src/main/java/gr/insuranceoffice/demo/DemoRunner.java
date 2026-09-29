package gr.insuranceoffice.demo;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import gr.insuranceoffice.demo.DemoSeeder.Account;
import gr.insuranceoffice.demo.DemoSeeder.Seeded;

/**
 * The demo (Task 39b): at startup, fills an empty database with synthetic
 * data and two accounts, prints the accounts, and the application then runs
 * as usual. Exists only under the {@code demo} profile, which
 * compose.demo.yaml activates, or in development:
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
 * </pre>
 *
 * Never on the office server, and two checks keep it off the office's data,
 * which fails both:
 * <ul>
 * <li>A database with another name than {@value #DATABASE_NAME} stops the
 * start, as the import refuses a database with data (Task 30). Checked before
 * Flyway migrates ({@link DemoMigrationStrategy}), so not even the schema of
 * a newer version is written, and here again.</li>
 * <li>A database with any data is left as it is: nothing is written, and the
 * application starts with what is there. That is the demo's own fill of an
 * earlier start; {@code docker compose -f compose.demo.yaml down -v} empties
 * it for a new one.</li>
 * </ul>
 * The passwords are random and printed once, on the start that makes them;
 * no file holds them.
 */
@Component
@Profile("demo")
public class DemoRunner implements ApplicationRunner {

	/** The one database the demo fills: compose.demo.yaml's, and application-demo.yml's default. */
	public static final String DATABASE_NAME = "insurance_office_demo";

	private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

	private final DataSource dataSource;

	private final DemoSeeder seeder;

	public DemoRunner(DataSource dataSource, DemoSeeder seeder) {
		this.dataSource = dataSource;
		this.seeder = seeder;
	}

	@Override
	public void run(ApplicationArguments args) {
		requireDemoDatabase(dataSource);
		seeder.seedIfEmpty().ifPresentOrElse(DemoRunner::printAccounts, () -> log.info("Η βάση «{}» έχει ήδη "
				+ "δεδομένα· δεν γράφτηκε τίποτα. Οι λογαριασμοί είναι όσοι γράφτηκαν στην πρώτη εκκίνηση. Για νέα "
				+ "δεδομένα και νέους κωδικούς: docker compose -f compose.demo.yaml down -v", DATABASE_NAME));
	}

	/**
	 * @throws IllegalStateException unless the database is the demo's. By the
	 *             name of the database connected to, not of the one
	 *             configured: they differ only by mistake, which is what this
	 *             is for.
	 */
	static void requireDemoDatabase(DataSource dataSource) {
		String database;
		try (Connection connection = dataSource.getConnection()) {
			database = connection.getCatalog();
		} catch (SQLException e) {
			throw new IllegalStateException("Δεν διαβάστηκε το όνομα της βάσης", e);
		}
		if (!DATABASE_NAME.equals(database)) {
			throw new IllegalStateException(("Το profile demo γεμίζει μόνο τη βάση «%s», όχι τη «%s». Δεν γράφτηκε "
					+ "τίποτα. Το demo ξεκινά με: docker compose -f compose.demo.yaml up").formatted(DATABASE_NAME,
							database));
		}
	}

	// In English as well: the demo is for anyone who cloned the repository.
	private static void printAccounts(Seeded seeded) {
		StringBuilder accounts = new StringBuilder();
		for (Account account : seeded.accounts()) {
			accounts.append("%n    %-13s %-6s %s".formatted(account.role(), account.username(), account.password()));
		}
		log.info("""

				================================================================
				DEMO: συνθετικά δεδομένα (synthetic data): {} πελάτες, {} οχήματα, {} συμβόλαια.
				http://127.0.0.1:8080, με τους λογαριασμούς (accounts):
				    ρόλος (role)  όνομα  κωδικός (password){}
				Οι κωδικοί γράφονται μόνο εδώ, μία φορά (shown only once).
				Για νέα δεδομένα και νέους κωδικούς (to start over):
				    docker compose -f compose.demo.yaml down -v
				================================================================""", seeded.customers(),
				seeded.vehicles(), seeded.policies(), accounts);
	}

}
