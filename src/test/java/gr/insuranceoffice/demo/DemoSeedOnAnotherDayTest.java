package gr.insuranceoffice.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Task 40: the demo fills its database through the real services on a day of
 * each kind of failure found, where two of a vehicle's policies overlapped by
 * the month arithmetic of its dates. {@link DemoDataEveryDayTest} checks every
 * day of a range by the same rules, written out; this ties them to
 * PolicyService.
 * <p>
 * The application starts on {@link DemoSeedTest#TODAY}, which has always
 * passed, so the startup fills the database as usual; each day empties it and
 * fills it again.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import({ DemoSeedTest.DemoDatabase.class, DemoSeedOnAnotherDayTest.MovableDay.class })
class DemoSeedOnAnotherDayTest {

	/** The application's clock, at noon in Athens of the day a test sets. */
	static final class DayClock extends Clock {

		private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

		private Instant now;

		DayClock(LocalDate day) {
			set(day);
		}

		synchronized void set(LocalDate day) {
			now = day.atTime(LocalTime.NOON).atZone(ATHENS).toInstant();
		}

		@Override
		public synchronized Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ATHENS;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MovableDay {

		@Bean
		@Primary
		DayClock dayClock() {
			return new DayClock(DemoSeedTest.TODAY);
		}

	}

	@Autowired
	private DemoSeeder seeder;

	@Autowired
	private DayClock clock;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// Before Task 40, each failed with an overlap of its kind:
	// 2026-10-11, the report: six months, ending on 31/12 by way of 30/06.
	// 2026-03-15: six months, ending on 30/08 by way of February (28/02).
	// 2027-10-14: twelve months, ending on 29/02/2028 of a leap year.
	@ParameterizedTest
	@CsvSource({ "2026-10-11, 632", "2026-03-15, 631", "2027-10-14, 632" })
	void fillsTheDatabaseThroughTheServices(LocalDate day, long policies) {
		jdbcTemplate.execute("TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer, app_user "
				+ "RESTART IDENTITY CASCADE");
		clock.set(day);

		assertThat(seeder.seedIfEmpty()).isPresent();

		assertThat(count("SELECT count(*) FROM customer")).isEqualTo(DemoData.CUSTOMERS);
		assertThat(count("SELECT count(*) FROM vehicle")).isEqualTo(236);
		assertThat(count("SELECT count(*) FROM policy")).isEqualTo(policies);
	}

	private long count(String sql) {
		return jdbcTemplate.queryForObject(sql, Long.class);
	}

}
