package gr.insuranceoffice.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.ExpiryPeriod;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.security.PasswordPolicy;
import gr.insuranceoffice.service.CustomerService;
import gr.insuranceoffice.service.DashboardService;
import gr.insuranceoffice.service.OwnershipService;
import gr.insuranceoffice.service.OwnershipService.Share;

/**
 * Task 39b: the application started with the demo profile, as
 * compose.demo.yaml starts it, on an empty database of the demo's name; the
 * runner fills it during startup. Tests that empty it fill it again before
 * they end, so each test finds it filled.
 * <p>
 * The demo's dates are counted from the application's today, so the day is
 * fixed here: the counts below are those of {@link #TODAY}, whatever day the
 * tests run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import({ DemoSeedTest.DemoDatabase.class, DemoSeedTest.FixedDay.class })
@ExtendWith(OutputCaptureExtension.class)
class DemoSeedTest {

	/** The only database the profile fills: its own, by name. */
	@TestConfiguration(proxyBeanMethods = false)
	static class DemoDatabase {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgresContainer() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
					.withDatabaseName(DemoRunner.DATABASE_NAME);
		}

	}

	static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

	/** The application's clock, stopped at noon of {@link #TODAY} in Athens. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedDay {

		@Bean
		@Primary
		Clock fixedClock() {
			ZoneId athens = ZoneId.of("Europe/Athens");
			return Clock.fixed(TODAY.atTime(LocalTime.NOON).atZone(athens).toInstant(), athens);
		}

	}

	// The tables the demo writes, and the columns to compare: all but the
	// times of writing and the password hashes, which are new on every run.
	private static final Map<String, String> TABLES = Map.of(
			"intermediary", "id, full_name, registry_number, phone, email, active",
			"customer", "id, tax_id, entity_type, last_name, first_name, father_name, birth_date, license_date, "
					+ "tax_office, street, city, postal_code, mobile, phone, email, notes, version",
			"vehicle", "id, vin, plate, plate_normalized, brand, model, first_registration, license_issue_date, "
					+ "category, usage_type, color, seats, engine_cc, power_kw, fuel_type, engine_number, co2, "
					+ "emission_standard, weight_kg, license_street, license_city, license_postal_code, version",
			"ownership", "id, vehicle_id, customer_id, percentage, is_primary, from_date, to_date",
			"policy", "id, policy_number, vehicle_id, insurance_company, intermediary_id, start_date, end_date, "
					+ "premium, surcharge, surcharge_type, version",
			"app_user", "id, username, full_name, role, active",
			"audit_log", "id, user_id, action, entity_type, entity_id");

	private static final Pattern ACCOUNT = Pattern.compile("(ΔΙΑΧΕΙΡΙΣΤΗΣ|ΥΠΑΛΛΗΛΟΣ) +(admin|clerk) +(\\S+)");

	@Autowired
	private DemoRunner runner;

	@Autowired
	private DemoSeeder seeder;

	@Autowired
	private CustomerService customerService;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void fillsTheEmptyDatabaseAtStartupThroughTheServices() {
		assertThat(count("SELECT count(*) FROM customer")).isEqualTo(DemoData.CUSTOMERS);
		assertThat(count("SELECT count(*) FROM vehicle")).isEqualTo(236);
		assertThat(count("SELECT count(*) FROM ownership")).isEqualTo(252);
		assertThat(count("SELECT count(*) FROM policy")).isEqualTo(647);
		assertThat(count("SELECT count(*) FROM intermediary")).isEqualTo(4);
		assertThat(jdbcTemplate.queryForList("SELECT username || ' ' || role FROM app_user ORDER BY id", String.class))
				.containsExactly("admin ΔΙΑΧΕΙΡΙΣΤΗΣ", "clerk ΥΠΑΛΛΗΛΟΣ");
		// Every row written through JPA, so audit_log has it (ARCHITECTURE §6).
		for (String entity : List.of("customer", "vehicle", "ownership", "policy", "intermediary")) {
			assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'CREATE' AND lower(entity_type) = '"
					+ entity + "'")).as(entity).isEqualTo(count("SELECT count(*) FROM " + entity));
		}
	}

	// Saved through CustomerService, so each passed its rules then; saved
	// again as they are, each passes them with everything else in place, the
	// mobile of the primary owner of an insured vehicle among them
	// (DECISIONS §1). Rolled back: nothing changes.
	@Test
	void everyCustomerPassesTheRulesOfCustomerService() {
		List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM customer ORDER BY id", Long.class);
		transactionTemplate.executeWithoutResult(status -> {
			for (Long id : ids) {
				CustomerDto customer = customerService.find(id);
				assertThatCode(() -> customerService.update(id, customer)).as("customer %d", id)
						.doesNotThrowAnyException();
			}
			status.setRollbackOnly();
		});
	}

	// DECISIONS §1, §2, and the synthetic series (.gitleaks.toml).
	@Test
	void someCustomersHaveNoTaxIdOrMobileAndTheRestAreSynthetic() {
		assertThat(count("SELECT count(*) FROM customer WHERE tax_id IS NULL")).isBetween(5L, 20L);
		assertThat(count("SELECT count(*) FROM customer WHERE mobile IS NULL")).isBetween(5L, 20L);
		assertThat(jdbcTemplate.queryForList("SELECT tax_id FROM customer WHERE tax_id IS NOT NULL", String.class))
				.allMatch(taxId -> taxId.matches("90000\\d{4}") && CustomerService.isValidTaxId(taxId));
		assertThat(jdbcTemplate.queryForList("SELECT mobile FROM customer WHERE mobile IS NOT NULL", String.class))
				.allMatch(mobile -> mobile.matches("6900000\\d{3}"));
		assertThat(jdbcTemplate.queryForList("SELECT email FROM customer WHERE email IS NOT NULL", String.class))
				.isNotEmpty().allMatch(email -> email.matches("[a-z]+\\.[a-z]+\\d*@example\\.com"));
		assertThat(jdbcTemplate.queryForList("SELECT plate FROM vehicle", String.class))
				.allMatch(plate -> plate.matches("[ΑΒΕΖΗΙΚΜΝΟΡΤΥΧ]{3}0\\d{3}"));
		assertThat(jdbcTemplate.queryForList("SELECT vin FROM vehicle", String.class))
				.allMatch(vin -> vin.matches("SYN[A-HJ-NPR-Z0-9]{8}\\d{6}"));
		// The surname in the right gender: a woman's never ends as a man's.
		assertThat(jdbcTemplate.queryForList("SELECT last_name FROM customer WHERE first_name IN ('Μαρία', "
				+ "'Ελένη', 'Αικατερίνη', 'Σοφία', 'Γεωργία', 'Δήμητρα', 'Ιωάννα', 'Ειρήνη')", String.class))
				.isNotEmpty().noneMatch(lastName -> lastName.matches(".*(ος|ης|ας)"));
	}

	@Test
	void everyVehicleHasSharesOf100AndOnePrimaryOwner() {
		Map<Long, List<Share>> shares = new LinkedHashMap<>();
		jdbcTemplate.query("SELECT vehicle_id, percentage, is_primary FROM ownership WHERE to_date IS NULL",
				row -> {
					shares.computeIfAbsent(row.getLong(1), id -> new ArrayList<>())
							.add(new Share(row.getBigDecimal(2), row.getBoolean(3)));
				});
		assertThat(shares).hasSize(236);
		shares.forEach((vehicle, owners) -> assertThat(OwnershipService.checkCurrentOwners(owners))
				.as("vehicle %d", vehicle).isEmpty());
		assertThat(shares.values().stream().filter(owners -> owners.size() == 2)).hasSizeGreaterThanOrEqualTo(10)
				.allMatch(owners -> owners.stream().allMatch(
						share -> share.percentage().compareTo(new BigDecimal(50)) == 0));
		// One former owner, whose policies stay theirs (Task 13).
		assertThat(count("SELECT count(*) FROM ownership WHERE to_date IS NOT NULL")).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM vehicle WHERE fuel_type = 'ΗΛΕΚΤΡΙΣΜΟΣ' AND engine_cc IS NULL"))
				.isPositive().isEqualTo(count("SELECT count(*) FROM vehicle WHERE fuel_type = 'ΗΛΕΚΤΡΙΣΜΟΣ'"));
		assertThat(jdbcTemplate.queryForList("SELECT DISTINCT category FROM vehicle", String.class))
				.contains("M1", "N1", "L3e");
		assertThat(count("SELECT count(*) FROM vehicle WHERE usage_type = 'ΤΑΞΙ'")).isPositive();
	}

	// SPEC §7.1: the home screen opens on renewals to do.
	@Test
	void theHomeScreenHasPoliciesEndingIn7And30Days() {
		int in7Days = dashboardService.expiries(ExpiryPeriod.DAYS_7, null).policies().size();
		int in30Days = dashboardService.expiries(ExpiryPeriod.DAYS_30, null).policies().size();

		assertThat(in7Days).isPositive();
		assertThat(in30Days).isGreaterThan(in7Days);
		assertThat(dashboardService.expiries(ExpiryPeriod.EXPIRED, null).policies()).isNotEmpty();
	}

	@Test
	void policiesHaveRenewalsOfSixAndTwelveMonthsAndOneStartsInTheFuture() {
		assertThat(count("SELECT count(*) FROM (SELECT vehicle_id FROM policy GROUP BY vehicle_id "
				+ "HAVING count(*) > 2) renewed")).isGreaterThan(100);
		assertThat(jdbcTemplate.queryForList("SELECT DISTINCT (end_date - start_date) / 30 FROM policy "
				+ "ORDER BY 1", Integer.class)).containsExactly(6, 12);
		assertThat(count("SELECT count(*) FROM policy WHERE start_date > '" + TODAY + "'")).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM policy WHERE intermediary_id IS NOT NULL")).isPositive();
		assertThat(jdbcTemplate.queryForList("SELECT DISTINCT insurance_company FROM policy", String.class))
				.allMatch(company -> company.matches("(Northwind|Contoso|Fabrikam|Woodgrove|Tailspin) Ασφαλιστική"));
	}

	@Test
	@WithMockUser
	void theHeaderSaysDemo() throws Exception {
		assertThat(html("/")).contains("id=\"demo-banner\">DEMO · συνθετικά δεδομένα</div>");
		assertThat(html("/customers")).contains("id=\"demo-banner\">DEMO · συνθετικά δεδομένα</div>");
	}

	@Test
	void twoRunsWithTheSameSeedGiveTheSameData() {
		Map<String, List<Map<String, Object>>> first = snapshot();

		empty();
		assertThat(seeder.seedIfEmpty()).isPresent();

		assertThat(snapshot()).isEqualTo(first);
	}

	@Test
	void writesNothingIntoADatabaseWithData() {
		Map<String, List<Map<String, Object>>> filled = snapshot();
		List<String> hashes = jdbcTemplate.queryForList("SELECT password_hash FROM app_user ORDER BY id", String.class);

		runner.run(null);

		assertThat(snapshot()).isEqualTo(filled);
		assertThat(jdbcTemplate.queryForList("SELECT password_hash FROM app_user ORDER BY id", String.class))
				.isEqualTo(hashes);

		// Any data, not only the demo's own.
		empty();
		try {
			customerService.create(new CustomerDto(null, null, "INDIVIDUAL", "Δοκιμαστικός", null, null, null, null,
					null, null, null, null, null, null, null, null, null));

			runner.run(null);

			assertThat(count("SELECT count(*) FROM customer")).isEqualTo(1);
			assertThat(count("SELECT count(*) FROM vehicle")).isZero();
			assertThat(count("SELECT count(*) FROM app_user")).isZero();
			assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(1);
		} finally {
			empty();
			seeder.seedIfEmpty();
		}
	}

	// Task 22a's rule; random on every run, never the seed's; printed, and
	// written to no file.
	@Test
	void printsTwoAccountsWithRandomPasswordsThatLogIn(CapturedOutput output) throws Exception {
		// The output of the start, captured too, printed other accounts.
		int started = output.getOut().length();
		empty();
		runner.run(null);
		Map<String, String> first = accounts(output.getOut().substring(started));

		assertThat(first).containsOnlyKeys("admin", "clerk");
		assertThat(output.getOut().substring(started)).contains("http://127.0.0.1:8080",
				"docker compose -f compose.demo.yaml down -v");
		for (Map.Entry<String, String> account : first.entrySet()) {
			String username = account.getKey();
			String password = account.getValue();
			assertThat(PasswordPolicy.check(password, username)).as(username).isEmpty();
			assertThat(passwordEncoder.matches(password,
					appUserRepository.findByUsername(username).orElseThrow().getPasswordHash())).as(username).isTrue();
			mockMvc.perform(formLogin("/login").user(username).password(password))
					.andExpect(authenticated().withUsername(username))
					.andExpect(redirectedUrl("/"));
		}
		assertThat(filesContaining(first.values())).isEmpty();

		int printed = output.getOut().length();
		empty();
		runner.run(null);
		Map<String, String> second = accounts(output.getOut().substring(printed));

		assertThat(second).containsOnlyKeys("admin", "clerk");
		assertThat(second.get("admin")).isNotEqualTo(first.get("admin")).isNotEqualTo(second.get("clerk"));
		assertThat(second.get("clerk")).isNotEqualTo(first.get("clerk"));
	}

	private String html(String path) throws Exception {
		return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static Map<String, String> accounts(String output) {
		Map<String, String> accounts = new LinkedHashMap<>();
		Matcher matcher = ACCOUNT.matcher(output);
		while (matcher.find()) {
			accounts.putIfAbsent(matcher.group(2), matcher.group(3));
		}
		return accounts;
	}

	// The project's own files: sources, docs, compose files; not the build's.
	private static List<Path> filesContaining(Collection<String> texts) throws IOException {
		Path root = Path.of("").toAbsolutePath();
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile)
					.filter(file -> !root.relativize(file).startsWith("target")
							&& !root.relativize(file).startsWith(".git"))
					.filter(file -> {
						try {
							String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
							return texts.stream().anyMatch(content::contains);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					})
					.collect(Collectors.toList());
		}
	}

	private Map<String, List<Map<String, Object>>> snapshot() {
		Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
		TABLES.forEach((table, columns) -> snapshot.put(table,
				jdbcTemplate.queryForList("SELECT " + columns + " FROM " + table + " ORDER BY id")));
		return snapshot;
	}

	private void empty() {
		jdbcTemplate.execute("TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer, app_user "
				+ "RESTART IDENTITY CASCADE");
	}

	private long count(String sql) {
		return jdbcTemplate.queryForObject(sql, Long.class);
	}

}
