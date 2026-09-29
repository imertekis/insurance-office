package gr.insuranceoffice.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import gr.insuranceoffice.InsuranceOfficeApplication;
import gr.insuranceoffice.TestcontainersConfiguration;

/**
 * Task 39b, without the demo profile: the normal start knows nothing of the
 * demo, and the profile refuses a database with another name than the
 * demo's. The database here is the tests' own, named "test".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoProfileTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private PostgreSQLContainer postgres;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	// Other test classes share this database: empty, so that nothing but the
	// name is wrong, and left empty.
	@BeforeEach
	void startEmpty() {
		empty();
	}

	@AfterEach
	void leaveEmpty() {
		empty();
	}

	@Test
	void isNotPartOfTheNormalStartup() {
		assertThat(context.getBeansOfType(DemoRunner.class)).isEmpty();
		assertThat(context.getBeansOfType(DemoSeeder.class)).isEmpty();
		assertThat(context.getBeansOfType(DemoMigrationStrategy.class)).isEmpty();
	}

	@Test
	@WithMockUser
	void theHeaderSaysNothingOfADemo() throws Exception {
		String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).contains("Ασφαλιστικό Γραφείο").doesNotContain("demo-banner").doesNotContain("DEMO");
	}

	// The application started as compose.demo.yaml starts it, but on a
	// database of another name, migrated as the office's is: it stops, and
	// writes nothing.
	@Test
	void refusesADatabaseWithAnotherNameAndWritesNothing() {
		assertThatThrownBy(() -> startDemoOn(postgres.getJdbcUrl()))
				.satisfies(refusal("μόνο τη βάση «insurance_office_demo», όχι τη «test»"));

		for (String table : new String[] { "customer", "vehicle", "policy", "intermediary", "app_user",
				"audit_log" }) {
			assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
		}
	}

	// Before Flyway: a database of another name gets not even the schema,
	// which a newer version would otherwise migrate before the refusal.
	@Test
	void refusesADatabaseWithAnotherNameBeforeMigratingIt() {
		jdbcTemplate.execute("CREATE DATABASE office");
		try {
			String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/office";

			assertThatThrownBy(() -> startDemoOn(url))
					.satisfies(refusal("μόνο τη βάση «insurance_office_demo», όχι τη «office»"));

			JdbcTemplate office = new JdbcTemplate(
					new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
			assertThat(office.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n "
					+ "ON n.oid = c.relnamespace WHERE n.nspname = 'public'", Long.class)).isZero();
		} finally {
			jdbcTemplate.execute("DROP DATABASE office WITH (FORCE)");
		}
	}

	// The runner's refusal, or Flyway's bean failing with it.
	private static ThrowingConsumer<Throwable> refusal(String message) {
		return thrown -> assertThat(NestedExceptionUtils.getMostSpecificCause(thrown))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(message);
	}

	// Its own context, beside the test's, with the demo profile. The database
	// as arguments, which win over application-demo.yml.
	private void startDemoOn(String url) {
		new SpringApplicationBuilder(InsuranceOfficeApplication.class)
				.profiles("demo")
				.web(WebApplicationType.NONE)
				.logStartupInfo(false)
				.run("--spring.datasource.url=" + url, "--spring.datasource.username=" + postgres.getUsername(),
						"--spring.datasource.password=" + postgres.getPassword());
	}

	private void empty() {
		jdbcTemplate.execute("TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer, app_user "
				+ "RESTART IDENTITY CASCADE");
	}

}
