package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;

/**
 * Task 28: the limits the forms and the import check are the database's, so
 * a migration that changes a column cannot leave an old limit behind in an
 * entity. Hibernate's schema validation checks types, not lengths.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ColumnLimitsTest {

	// The entities the forms and the Excel import write text to.
	private static final List<Class<?>> WRITTEN = List.of(Customer.class, Vehicle.class, Policy.class,
			Intermediary.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void equalsTheDatabaseForEveryTextColumnWritten() {
		List<String> checked = new ArrayList<>();
		List<String> withoutLimit = new ArrayList<>();
		for (Class<?> entity : WRITTEN) {
			String table = entity.getAnnotation(Table.class).name();
			for (Field field : entity.getDeclaredFields()) {
				Column column = field.getAnnotation(Column.class);
				// Generated columns (search_normalized, name_sort) are never written.
				if (column == null || field.getType() != String.class || !column.insertable()) {
					continue;
				}
				String name = table + "." + column.name();
				Map<String, Object> database = jdbcTemplate.queryForMap("""
						SELECT data_type, character_maximum_length FROM information_schema.columns
						WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
						""", table, column.name());
				if ("character varying".equals(database.get("data_type"))) {
					assertThat(ColumnLimits.of(entity, field.getName())).as(name)
							.isEqualTo(((Number) database.get("character_maximum_length")).intValue());
					checked.add(name);
				} else {
					withoutLimit.add(name + " " + database.get("data_type"));
				}
			}
		}

		// The one text column without a limit, and it stays so (Task 28).
		assertThat(withoutLimit).containsExactly("customer.notes text");
		// Every other VARCHAR column of these tables holds an enum, whose
		// values the forms and the import check against the enum itself.
		List<String> others = jdbcTemplate.queryForList("""
				SELECT table_name || '.' || column_name FROM information_schema.columns
				WHERE table_schema = current_schema() AND data_type = 'character varying'
				AND table_name IN ('customer', 'vehicle', 'policy', 'intermediary')
				""", String.class);
		others.removeAll(checked);
		assertThat(others).containsExactlyInAnyOrder("customer.entity_type", "vehicle.usage_type",
				"vehicle.fuel_type", "policy.surcharge_type");
	}

	// Letters, accents typed as a separate mark, a letter outside the BMP,
	// an emoji built of several code points, a no-break space.
	@ParameterizedTest
	@ValueSource(strings = { "Αλεξίου", "ΑΛΕΞΙΟΥ", "Αλεξίου", "𝔸𝔹 Ω", "👩‍💻", "Οδός 1" })
	void countsCharactersAsPostgreSqlDoes(String text) {
		assertThat(ColumnLimits.length(text))
				.isEqualTo(jdbcTemplate.queryForObject("SELECT char_length(?)", Integer.class, text));
	}

	@Test
	void readsTheLengthFromTheEntity() {
		assertThat(ColumnLimits.of(Customer.class, "lastName")).isEqualTo(100);
		assertThat(ColumnLimits.of(Vehicle.class, "licenseStreet")).isEqualTo(200);
	}

	// A bug in the caller, not a value to check.
	@Test
	void refusesAFieldThatIsNotATextColumn() {
		assertThatThrownBy(() -> ColumnLimits.of(Customer.class, "lastname"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ColumnLimits.of(Vehicle.class, "usageType"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ColumnLimits.of(Vehicle.class, "ownerships"))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
