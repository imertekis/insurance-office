package gr.insuranceoffice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * V4 cleans the plates already stored (Task 14). Runs on a database of its
 * own, migrated to V3 first, so that the rows go through the migration the
 * way the office's do.
 */
class PlateSeparatorsMigrationTest {

	@Test
	void stripsDashesAndSpacesAndLeavesTheRest() {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
			postgres.start();
			DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword());
			flyway(dataSource, "3").migrate();

			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			insert(jdbc, "WVWZZZ1KZAW000001", "ΝΚΝ-7777");
			insert(jdbc, "WVWZZZ1KZAW000002", "ΑΒΕ 1234");
			insert(jdbc, "WVWZZZ1KZAW000003", "ΚΜΝ–4321"); // en dash
			insert(jdbc, "WVWZZZ1KZAW000004", "ΗΚΝ9012"); // nothing to strip
			insert(jdbc, "WVWZZZ1KZAW000005", "ΕΣ-12345"); // Greek-only letters stay
			insert(jdbc, "WVWZZZ1KZAW000006", "nza-8812"); // case stays

			flyway(dataSource, "latest").migrate();

			List<String> plates = jdbc.queryForList("SELECT plate FROM vehicle ORDER BY vin", String.class);
			assertThat(plates).containsExactly("ΝΚΝ7777", "ΑΒΕ1234", "ΚΜΝ4321", "ΗΚΝ9012", "ΕΣ12345", "nza8812");
			// The search side was already stripped and does not move.
			assertThat(jdbc.queryForList("SELECT plate_normalized FROM vehicle ORDER BY vin", String.class))
					.containsExactly("NKN7777", "ABE1234", "KMN4321", "HKN9012", "EΣ12345", "NZA8812");
		}
	}

	private static Flyway flyway(DataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).target(target).load();
	}

	private static void insert(JdbcTemplate jdbc, String vin, String plate) {
		jdbc.update("""
				INSERT INTO vehicle (vin, plate, plate_normalized, brand, model, first_registration, category,
					usage_type, color, power_kw, fuel_type)
				VALUES (?, ?, ?, 'Volkswagen', 'Golf', DATE '2012-05-14', 'M1', 'ΕΙΧ', 'Λευκό', 81, 'ΒΕΝΖΙΝΗ')
				""", vin, plate,
				// What the Java callback stored before Task 14.
				TextNormalizationUtils.normalizePlate(plate));
	}

}
