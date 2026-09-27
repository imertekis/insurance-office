package gr.insuranceoffice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import gr.insuranceoffice.service.VehicleValues;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * V8 gives the vehicles already stored the values of the lists (Task 23a),
 * as the Excel import maps them. Runs on a database of its own, migrated to
 * V7 first, so that the rows go through the migration the way the office's
 * do.
 */
class VehicleValuesMigrationTest {

	// brand, category, colour, Euro, seats: as the development database and
	// the office's files write them.
	private static final List<Row> DIRTY = List.of(
			new Row("VW", "M1", "ΜΑΥΡΟ", "Euro 6d-TEMP", 5),
			new Row("AUDI", "Ι.Χ.", "Λευκό", "Euro 5", null),
			// A Greek Μ, a slash between two colours, a letter after the number.
			new Row("Ford", "Μ1", "ΑΣΠΡΟ / ΜΑΥΡΟ", "EURO 5b", 0),
			new Row("mercedes benz", "n1", "ΛΑΔΙ", "ZEV", 3),
			// Nothing to do.
			new Row("Toyota", "M1", "Λευκό", "Euro 6", 5),
			// Nothing matches but the category.
			new Row("ΦΙΑΤ", "l3e", "Λευκό-Μαύρο-Κόκκινο", "Euro VI", 2),
			new Row("Citroen", "M1", "ασημί", "euro 4", null),
			new Row("V.W.", "M1", "Γκρίζο", null, 5));

	@Test
	void mapsTheStoredValuesOntoTheListsAndLogsEachChange() {
		try (PostgreSQLContainer postgres = postgres()) {
			DataSource dataSource = dataSource(postgres);
			flyway(dataSource, "7").migrate();
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			for (int i = 0; i < DIRTY.size(); i++) {
				insert(jdbc, i + 1, DIRTY.get(i));
			}

			flyway(dataSource, "latest").migrate();

			assertThat(stored(jdbc)).containsExactly(
					new Row("Volkswagen", "M1", "Μαύρο", "Euro 6", 5),
					new Row("Audi", "Ι.Χ.", "Λευκό", "Euro 5", null),
					new Row("Ford", "M1", "Λευκό-Μαύρο", "Euro 5", null),
					new Row("Mercedes-Benz", "N1", "ΛΑΔΙ", "ZEV", 3),
					new Row("Toyota", "M1", "Λευκό", "Euro 6", 5),
					new Row("ΦΙΑΤ", "L3e", "Λευκό-Μαύρο-Κόκκινο", "Euro VI", 2),
					new Row("Citroën", "M1", "Ασημί", "Euro 4", null),
					new Row("Volkswagen", "M1", "Γκρι", null, 5));
			assertThat(jdbc.queryForList("SELECT version FROM vehicle ORDER BY id", Long.class))
					.containsExactly(1L, 1L, 1L, 1L, 0L, 1L, 1L, 1L);

			// One UPDATE row per changed vehicle, only the columns that changed.
			List<Map<String, Object>> audit = jdbc.queryForList(
					"SELECT entity_id, action, entity_type, user_id FROM audit_log ORDER BY entity_id");
			assertThat(audit).extracting(row -> ((Number) row.get("entity_id")).longValue())
					.containsExactly(1L, 2L, 3L, 4L, 6L, 7L, 8L);
			assertThat(audit).allSatisfy(row -> {
				assertThat(row.get("action")).isEqualTo("UPDATE");
				assertThat(row.get("entity_type")).isEqualTo("Vehicle");
				assertThat(row.get("user_id")).isNull();
			});
			assertLogged(jdbc, 1,
					"{\"brand\": \"VW\", \"color\": \"ΜΑΥΡΟ\", \"emission_standard\": \"Euro 6d-TEMP\"}",
					"{\"brand\": \"Volkswagen\", \"color\": \"Μαύρο\", \"emission_standard\": \"Euro 6\"}");
			// «Ι.Χ.» stays, so only the brand is in the log.
			assertLogged(jdbc, 2, "{\"brand\": \"AUDI\"}", "{\"brand\": \"Audi\"}");
			// A change to empty is a null, as the audit listener writes it.
			assertLogged(jdbc, 3,
					"{\"category\": \"Μ1\", \"color\": \"ΑΣΠΡΟ / ΜΑΥΡΟ\", \"emission_standard\": \"EURO 5b\", "
							+ "\"seats\": 0}",
					"{\"category\": \"M1\", \"color\": \"Λευκό-Μαύρο\", \"emission_standard\": \"Euro 5\", "
							+ "\"seats\": null}");
		}
	}

	// The SQL of V8 is a second copy of the mapping; for every value above it
	// must give what VehicleValues gives, the brands read from the same table.
	@Test
	void mapsEachValueAsTheImportDoes() {
		try (PostgreSQLContainer postgres = postgres()) {
			DataSource dataSource = dataSource(postgres);
			flyway(dataSource, "7").migrate();
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			List<Row> dirty = new ArrayList<>(DIRTY);
			// More spellings, one vehicle each.
			for (String brand : List.of("volkswagen", "MERCEDES", "Mercedes-Benz", "SKODA", "land-rover", "Άγνωστη")) {
				dirty.add(new Row(brand, "M1", "Λευκό", null, null));
			}
			for (String category : List.of("M 1", "Ν3", "Ο4", "Τ", "L7E", "M1G", "Ι.Χ")) {
				dirty.add(new Row("Toyota", category, "Λευκό", null, null));
			}
			for (String colour : List.of("λευκο", "ΠΟΛΥΧΡΩΜΟ", "Μαύρο-Μαύρο", "ΧΡΥΣΟ-ΜΠΛΕ", "ΔΙΧΡΩΜΟ", "Λευκό-")) {
				dirty.add(new Row("Toyota", "M1", colour, null, null));
			}
			for (String euro : List.of("Euro6", "EURO 6 d", "Euro 61", "Euro 7", "zev", "Euro 3 ", "E6")) {
				dirty.add(new Row("Toyota", "M1", "Λευκό", euro, null));
			}
			for (int i = 0; i < dirty.size(); i++) {
				insert(jdbc, i + 1, dirty.get(i));
			}
			VehicleValues.Brands brands = new VehicleValues.Brands(jdbc.queryForList(
					"SELECT name, array_to_string(synonyms, '|') AS synonyms FROM vehicle_brand").stream()
					.collect(Collectors.toMap(row -> (String) row.get("name"),
							row -> Arrays.stream(((String) row.get("synonyms")).split("\\|"))
									.filter(synonym -> !synonym.isEmpty()).toList())));

			flyway(dataSource, "latest").migrate();

			assertThat(stored(jdbc)).containsExactlyElementsOf(dirty.stream()
					.map(row -> new Row(brands.match(row.brand()).value(),
							VehicleValues.category(row.category()).value(),
							VehicleValues.color(row.color()).value(),
							row.euro() == null ? null : VehicleValues.emissionStandard(row.euro()).value(),
							row.seats() == null || row.seats() == 0 ? null : row.seats()))
					.toList());
		}
	}

	private static void assertLogged(JdbcTemplate jdbc, long vehicleId, String oldValues, String newValues) {
		assertThat(jdbc.queryForMap("""
				SELECT old_values = CAST(? AS jsonb) AS old_matches, new_values = CAST(? AS jsonb) AS new_matches,
				       old_values::text AS old_values, new_values::text AS new_values
				  FROM audit_log WHERE entity_id = ?
				""", oldValues, newValues, vehicleId))
				.as("audit of vehicle %d", vehicleId)
				.containsEntry("old_matches", true).containsEntry("new_matches", true);
	}

	private record Row(String brand, String category, String color, String euro, Integer seats) {
	}

	private static List<Row> stored(JdbcTemplate jdbc) {
		return jdbc.query("SELECT brand, category, color, emission_standard, seats FROM vehicle ORDER BY id",
				(rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getObject(5) == null ? null : rs.getInt(5)));
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

	private static void insert(JdbcTemplate jdbc, int number, Row row) {
		String plate = "ΑΒΕ" + (1000 + number);
		jdbc.update("""
				INSERT INTO vehicle (vin, plate, plate_normalized, brand, model, first_registration, category,
					usage_type, color, seats, power_kw, fuel_type, emission_standard)
				VALUES (?, ?, ?, ?, 'Μοντέλο', DATE '2012-05-14', ?, 'ΕΙΧ', ?, ?, 81, 'ΒΕΝΖΙΝΗ', ?)
				""", "WVWZZZ1KZAW%06d".formatted(number), plate, TextNormalizationUtils.normalizePlate(plate),
				row.brand(), row.category(), row.color(), row.seats(), row.euro());
	}

}
