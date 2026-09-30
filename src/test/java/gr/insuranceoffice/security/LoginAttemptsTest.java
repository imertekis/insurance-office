package gr.insuranceoffice.security;

import static gr.insuranceoffice.security.LoginAttempts.KEPT_NAME_LENGTH;
import static gr.insuranceoffice.security.LoginAttempts.MAX_FAILURES;
import static gr.insuranceoffice.security.LoginAttempts.MAX_NAMES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.security.LoginAttempts.LockedOutException;

/**
 * Task 22b: five failed logins in a row lock a name out for five minutes. The
 * login itself stands in as a lambda, and the clock is moved by hand.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginAttemptsTest {

	private static final Authentication SUCCESS = new TestingAuthenticationToken("maria", null);

	// One instance: thousands of logins in the tests of the size limit.
	private static final BadCredentialsException WRONG = new BadCredentialsException("Λάθος κωδικός");

	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

	private final MutableClock clock = new MutableClock();

	private final LoginAttempts attempts = new LoginAttempts(appUserRepository, clock);

	@Test
	void letsTheFifthLoginCheckItsPasswordAfterFourFailures() {
		failTimes("maria", MAX_FAILURES - 1);

		assertLogsIn("maria");
	}

	@Test
	void locksTheNameOutAtTheFifthFailureEvenForTheRightPassword() {
		failTimes("maria", MAX_FAILURES);

		assertLockedOut("maria");
	}

	// Measured from the fifth failure; the refused logins do not extend it.
	@Test
	void letsTheNameInAgainFiveMinutesLater() {
		failTimes("maria", MAX_FAILURES);

		clock.advance(Duration.ofMinutes(4));
		assertLockedOut("maria");
		clock.advance(Duration.ofSeconds(59));
		assertLockedOut("maria");
		clock.advance(Duration.ofSeconds(1));

		assertLogsIn("maria");
	}

	@Test
	void startsANewCountOnceTheLockoutIsOver() {
		failTimes("maria", MAX_FAILURES);
		clock.advance(LoginAttempts.LOCKOUT);

		failTimes("maria", MAX_FAILURES - 1);
		fail("maria");

		assertLockedOut("maria");
	}

	@Test
	void startsTheCountOverAfterASuccess() {
		failTimes("maria", MAX_FAILURES - 1);
		assertLogsIn("maria");
		failTimes("maria", MAX_FAILURES - 1);

		assertLogsIn("maria");
	}

	// As typed: the login itself tells upper and lower case apart.
	@Test
	void countsEachNameOnItsOwn() {
		failTimes("maria", MAX_FAILURES);

		assertLockedOut("maria");
		assertLogsIn("Maria");
		assertLogsIn("nikos");
	}

	// No account, no password: the server failed (the database, say).
	@Test
	void doesNotCountALoginTheServerCouldNotAnswer() {
		for (int i = 0; i < MAX_FAILURES; i++) {
			assertThatThrownBy(() -> attempts.authenticate("maria", () -> {
				throw new InternalAuthenticationServiceException("Η βάση δεν απαντά");
			})).isInstanceOf(InternalAuthenticationServiceException.class);
			assertThatThrownBy(() -> attempts.authenticate("maria", () -> {
				throw new IllegalStateException("Σφάλμα");
			})).isInstanceOf(IllegalStateException.class);
		}

		assertLogsIn("maria");
	}

	// Five logins at once cannot all check a password before the first fails.
	@Test
	void holdsATryForEachLoginStillRunning() throws Exception {
		CountDownLatch checking = new CountDownLatch(MAX_FAILURES);
		CountDownLatch answer = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(MAX_FAILURES);
		try {
			List<Future<Authentication>> running = new ArrayList<>();
			for (int i = 0; i < MAX_FAILURES; i++) {
				running.add(executor.submit(() -> attempts.authenticate("maria", () -> {
					checking.countDown();
					awaitQuietly(answer);
					throw WRONG;
				})));
			}
			assertThat(checking.await(10, SECONDS)).isTrue();

			assertLockedOut("maria");

			answer.countDown();
			for (Future<Authentication> login : running) {
				assertThatThrownBy(login::get).hasCause(WRONG);
			}
			assertLockedOut("maria");
		}
		finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
		}
	}

	@Test
	void keepsNoMoreThanTheLimitOfNames() {
		for (int i = 0; i < MAX_NAMES + 500; i++) {
			fail("script-" + i);
		}

		assertThat(attempts.size()).isEqualTo(MAX_NAMES);
	}

	@Test
	void forgetsTheNameTriedLongestAgoFirst() {
		failTimes("maria", MAX_FAILURES - 1);
		for (int i = 0; i < MAX_NAMES; i++) {
			fail("script-" + i);
		}

		// Its four failures are gone: a fifth does not lock it.
		fail("maria");
		assertLogsIn("maria");
	}

	// Otherwise enough random names would lift a lockout early.
	@Test
	void keepsALockedOutNameWhileThereIsAnyOtherToForget() {
		failTimes("maria", MAX_FAILURES);
		for (int i = 0; i < 2 * MAX_NAMES; i++) {
			fail("script-" + i);
		}

		assertThat(attempts.size()).isEqualTo(MAX_NAMES);
		assertLockedOut("maria");
	}

	// No account has a name that long (50 characters at most).
	@Test
	void keepsAnOverlongNameByItsBeginningOnly() {
		String beginning = "α".repeat(KEPT_NAME_LENGTH);
		failTimes(beginning + "β".repeat(1000), 3);
		failTimes(beginning + "γ".repeat(1000), 2);

		assertLockedOut(beginning + "δ");
		assertLogsIn(beginning.substring(1));
	}

	@Test
	void logsTheLockoutWithTheTimeAndTheNameOfAnAccount(CapturedOutput output) {
		when(appUserRepository.findByUsername("maria")).thenReturn(Optional.of(new AppUser()));

		failTimes("maria", MAX_FAILURES);
		assertLockedOut("maria");
		assertLockedOut("maria");

		// 09:00 UTC is 12:00 in Athens in September.
		assertThat(output.getAll()).containsOnlyOnce("Κλείδωμα σύνδεσης")
				.contains("Κλείδωμα σύνδεσης για τον λογαριασμό «maria» μετά από 5 αποτυχημένες προσπάθειες, "
						+ "στις 27/09/2026 12:00:00, για 5 λεπτά.");
	}

	// Task 22, decision 5: a password typed into the name field by mistake stays out of the log.
	@Test
	void logsTheLockoutOfANameWithoutAnAccountWithoutTheName(CapturedOutput output) {
		failTimes("κ7x9-πληκτρολογημένος", MAX_FAILURES);

		assertThat(output.getAll())
				.contains("Κλείδωμα σύνδεσης για άγνωστο όνομα μετά από 5 αποτυχημένες προσπάθειες, "
						+ "στις 27/09/2026 12:00:00, για 5 λεπτά.")
				.doesNotContain("κ7x9");
	}

	private void failTimes(String username, int times) {
		for (int i = 0; i < times; i++) {
			fail(username);
		}
	}

	private void fail(String username) {
		assertThatThrownBy(() -> attempts.authenticate(username, () -> {
			throw WRONG;
		})).isSameAs(WRONG);
	}

	private void assertLogsIn(String username) {
		assertThat(attempts.authenticate(username, () -> SUCCESS)).isSameAs(SUCCESS);
	}

	// Refused before the account or the password is looked at.
	private void assertLockedOut(String username) {
		Supplier<Authentication> login = () -> {
			throw new AssertionError("The password of a locked-out name was checked.");
		};
		assertThatThrownBy(() -> attempts.authenticate(username, login)).isInstanceOf(LockedOutException.class);
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
