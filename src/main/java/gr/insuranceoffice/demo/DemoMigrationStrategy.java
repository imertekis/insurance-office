package gr.insuranceoffice.demo;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Task 39b: under the demo profile, the database is checked before Flyway
 * migrates it. Started by mistake on another database, the demo stops before
 * writing anything there, the schema of a newer version included; the
 * runner would refuse it only after the migrations.
 */
@Component
@Profile("demo")
public class DemoMigrationStrategy implements FlywayMigrationStrategy {

	@Override
	public void migrate(Flyway flyway) {
		DemoRunner.requireDemoDatabase(flyway.getConfiguration().getDataSource());
		flyway.migrate();
	}

}
