package gr.insuranceoffice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * V6 puts the plates and VINs already stored in capitals (Task 17). Runs on a
 * database of its own, migrated to V5 first, so that the rows go through the
 * migration the way the office's do.
 */
class UppercasePlateAndVinMigrationTest {

	@Test
	void upperCasesPlatesAndVinsAndLogsEachChange() {
		try (PostgreSQLContainer postgres = postgres()) {
			DataSource dataSource = dataSource(postgres);
			flyway(dataSource, "5").migrate();
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			insert(jdbc, "wvwzzz1kzaw000001", "νκν7777"); // both change
			insert(jdbc, "WVWZZZ1KZAW000002", "άβε1234"); // accents go
			insert(jdbc, "WVWZZZ1KZAW000003", "abe9876"); // stays Latin
			insert(jdbc, "WVWZZZ1KZAW000004", "ΗΚΝ9012"); // nothing to do
			insert(jdbc, "WVWZZZ1KZAW000005", "εσ12345"); // Greek-only letters
			List<String> normalizedBefore = column(jdbc, "plate_normalized");

			flyway(dataSource, "latest").migrate();

			assertThat(column(jdbc, "plate")).containsExactly("ΝΚΝ7777", "ΑΒΕ1234", "ABE9876", "ΗΚΝ9012", "ΕΣ12345");
			// The Latin plate is still Latin, the Greek ones still Greek.
			assertThat(column(jdbc, "plate").get(2)).isEqualTo("ABE" + "9876");
			assertThat(column(jdbc, "plate").get(1)).isEqualTo("ΑΒΕ" + "1234");
			assertThat(column(jdbc, "vin")).containsExactly("WVWZZZ1KZAW000001", "WVWZZZ1KZAW000002",
					"WVWZZZ1KZAW000003", "WVWZZZ1KZAW000004", "WVWZZZ1KZAW000005");
			assertThat(column(jdbc, "plate_normalized")).isEqualTo(normalizedBefore);
			assertThat(jdbc.queryForList("SELECT version FROM vehicle ORDER BY id", Long.class))
					.containsExactly(1L, 1L, 1L, 0L, 1L);

			// One UPDATE row per changed vehicle, only the columns that changed.
			List<Map<String, Object>> audit = jdbc.queryForList("""
					SELECT v.plate, a.action, a.entity_type, a.user_id,
					       a.old_values ->> 'plate' AS old_plate, a.new_values ->> 'plate' AS new_plate,
					       a.old_values ->> 'vin' AS old_vin, a.new_values ->> 'vin' AS new_vin
					  FROM audit_log a JOIN vehicle v ON v.id = a.entity_id
					 ORDER BY a.entity_id
					""");
			assertThat(audit).hasSize(4);
			assertThat(audit).allSatisfy(row -> {
				assertThat(row.get("action")).isEqualTo("UPDATE");
				assertThat(row.get("entity_type")).isEqualTo("Vehicle");
				assertThat(row.get("user_id")).isNull();
			});
			assertThat(audit.get(0)).containsEntry("old_plate", "νκν7777").containsEntry("new_plate", "ΝΚΝ7777")
					.containsEntry("old_vin", "wvwzzz1kzaw000001").containsEntry("new_vin", "WVWZZZ1KZAW000001");
			assertThat(audit.get(1)).containsEntry("old_plate", "άβε1234").containsEntry("new_plate", "ΑΒΕ1234")
					.containsEntry("old_vin", null).containsEntry("new_vin", null);
			assertThat(audit.stream().map(row -> row.get("plate"))).doesNotContain("ΗΚΝ9012");
		}
	}

	// The VIN index is case-sensitive, so these two exist today; upper-casing
	// them would make them one. The migration must refuse, not merge.
	@Test
	void stopsOnVinsThatDifferOnlyInCaseAndChangesNothing() {
		try (PostgreSQLContainer postgres = postgres()) {
			DataSource dataSource = dataSource(postgres);
			flyway(dataSource, "5").migrate();
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			insert(jdbc, "wvwzzz1kzaw000009", "νκν7777");
			insert(jdbc, "WVWZZZ1KZAW000009", "ΑΒΕ1234");

			assertThatThrownBy(() -> flyway(dataSource, "latest").migrate())
					.hasStackTraceContaining("WVWZZZ1KZAW000009")
					.hasStackTraceContaining("πεζά/κεφαλαία");

			assertThat(column(jdbc, "plate")).containsExactly("νκν7777", "ΑΒΕ1234");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isZero();
			assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history WHERE success",
					String.class)).isEqualTo("5");
		}
	}

	private static PostgreSQLContainer postgres() {
		PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
		postgres.start();
		return postgres;
	}

	private static DataSource dataSource(PostgreSQLContainer postgres) {
		return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private static Flyway flyway(DataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).target(target).load();
	}

	private static List<String> column(JdbcTemplate jdbc, String column) {
		return jdbc.queryForList("SELECT " + column + " FROM vehicle ORDER BY id", String.class);
	}

	private static void insert(JdbcTemplate jdbc, String vin, String plate) {
		jdbc.update("""
				INSERT INTO vehicle (vin, plate, plate_normalized, brand, model, first_registration, category,
					usage_type, color, power_kw, fuel_type)
				VALUES (?, ?, ?, 'Volkswagen', 'Golf', DATE '2012-05-14', 'M1', 'ΕΙΧ', 'Λευκό', 81, 'ΒΕΝΖΙΝΗ')
				""", vin, plate,
				// What the Java callback stored before Task 17.
				TextNormalizationUtils.normalizePlate(plate));
	}

}
