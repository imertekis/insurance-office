package gr.insuranceoffice.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import gr.insuranceoffice.repository.AppUserRepository;

/**
 * The limit on failed logins (Task 22b, decision 3): after five failed
 * logins in a row with one username, that name is refused for five minutes,
 * even with the right password. A successful login starts the count over.
 * There is no limit per IP address: the office goes out through one address,
 * and such a limit would lock everyone out together.
 * <p>
 * Counted per name as typed, whether an account has it or not (decision 4),
 * so a name without an account locks in the same way and the login page
 * never tells which accounts exist. While a name is locked out, neither its
 * account nor its password is looked at, so the answer takes the same time
 * for both. Each lockout goes to the application log, with the name only
 * when an account has it (decision 5).
 * <p>
 * Kept in memory, not in {@code app_user}: names without an account count
 * too, and they have no row there. A restart forgets everything. At most
 * {@link #MAX_NAMES} names are kept, so a script trying random names cannot
 * fill the memory.
 */
@Component
public class LoginAttempts {

	private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);

	static final int MAX_FAILURES = 5;

	static final Duration LOCKOUT = Duration.ofMinutes(5);

	static final int MAX_NAMES = 10_000;

	/**
	 * How much of a name is kept. More than {@code app_user.username} holds
	 * (50), so every name an account can have is kept whole. A longer one,
	 * which no account can have, shares its count with those that begin the
	 * same way: kept whole, names of a few megabytes each would fill the
	 * memory long before the limit on names.
	 */
	static final int KEPT_NAME_LENGTH = 100;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	private final AppUserRepository appUserRepository;

	private final Clock clock;

	/** In access order: the first is the name tried longest ago. */
	private final LinkedHashMap<String, Tries> names = new LinkedHashMap<>(16, 0.75f, true);

	public LoginAttempts(AppUserRepository appUserRepository, Clock clock) {
		this.appUserRepository = appUserRepository;
		this.clock = clock;
	}

	/**
	 * Runs a login with this name, unless the name is locked out, and counts
	 * how it ends.
	 *
	 * @param username the name as typed
	 * @param login looks up the account and checks the password
	 * @return what {@code login} returns
	 * @throws LockedOutException while the name is locked out; {@code login}
	 *         is not run
	 */
	public Authentication authenticate(String username, Supplier<Authentication> login) {
		String name = kept(username);
		if (!start(name)) {
			throw new LockedOutException();
		}
		boolean answered = false;
		try {
			Authentication success = login.get();
			answered = true;
			succeeded(name);
			return success;
		}
		catch (InternalAuthenticationServiceException ex) {
			// The server failed (the database, say), not the name or the
			// password: not counted.
			throw ex;
		}
		catch (AuthenticationException ex) {
			answered = true;
			failed(name, username);
			throw ex;
		}
		finally {
			if (!answered) {
				abandoned(name);
			}
		}
	}

	/**
	 * Whether a login may check its password. Logins still running hold a try
	 * each, so five at once cannot all be checked before the first one fails.
	 */
	private synchronized boolean start(String name) {
		Instant now = clock.instant();
		Tries tries = names.get(name);
		if (tries == null) {
			makeRoom(now);
			tries = new Tries();
			names.put(name, tries);
		}
		else if (tries.lockedUntil != null) {
			if (tries.isLockedOut(now)) {
				return false;
			}
			// The five minutes are over: a new count.
			tries.failures = 0;
			tries.lockedUntil = null;
		}
		if (tries.failures + tries.running >= MAX_FAILURES) {
			return false;
		}
		tries.running++;
		return true;
	}

	private synchronized void succeeded(String name) {
		Tries tries = names.get(name);
		if (tries == null) {
			return;
		}
		tries.finished();
		if (tries.running == 0) {
			names.remove(name);
		}
		else {
			tries.failures = 0;
			tries.lockedUntil = null;
		}
	}

	private void failed(String name, String username) {
		Instant now;
		synchronized (this) {
			Tries tries = names.get(name);
			if (tries == null) {
				return;
			}
			tries.finished();
			tries.failures++;
			if (tries.lockedUntil != null || tries.failures < MAX_FAILURES) {
				return;
			}
			now = clock.instant();
			tries.lockedUntil = now.plus(LOCKOUT);
		}
		// Outside the lock: other names need not wait for the database.
		logLockout(username, now);
	}

	/** The login ended without an answer on the password: its try is given back. */
	private synchronized void abandoned(String name) {
		Tries tries = names.get(name);
		if (tries == null) {
			return;
		}
		tries.finished();
		if (tries.running == 0 && tries.failures == 0 && tries.lockedUntil == null) {
			names.remove(name);
		}
	}

	/**
	 * The name tried longest ago goes first. A name that is locked out, or
	 * has a login running, is kept while there is any other: otherwise enough
	 * random names would lift a lockout early.
	 */
	private void makeRoom(Instant now) {
		if (names.size() < MAX_NAMES) {
			return;
		}
		for (Iterator<Tries> eldestFirst = names.values().iterator(); eldestFirst.hasNext();) {
			Tries tries = eldestFirst.next();
			if (!tries.isLockedOut(now) && tries.running == 0) {
				eldestFirst.remove();
				return;
			}
		}
		names.pollFirstEntry();
	}

	/**
	 * The name only if an account has it: a password typed into the name
	 * field by mistake must not reach the log (decision 5).
	 */
	private void logLockout(String username, Instant now) {
		String who = appUserRepository.findByUsername(username).isPresent()
				? "τον λογαριασμό «" + username + "»"
				: "άγνωστο όνομα";
		log.warn("Κλείδωμα σύνδεσης για {} μετά από {} αποτυχημένες προσπάθειες, στις {}, για {} λεπτά.", who,
				MAX_FAILURES, TIME.format(now.atZone(clock.getZone())), LOCKOUT.toMinutes());
	}

	/** Package-private for the tests of the size limit. */
	synchronized int size() {
		return names.size();
	}

	private static String kept(String username) {
		String name = username == null ? "" : username;
		return name.length() > KEPT_NAME_LENGTH ? name.substring(0, KEPT_NAME_LENGTH) : name;
	}

	private static final class Tries {

		/** Failed logins in a row. */
		int failures;

		/** Logins that have started and not yet ended. */
		int running;

		Instant lockedUntil;

		boolean isLockedOut(Instant now) {
			return lockedUntil != null && now.isBefore(lockedUntil);
		}

		void finished() {
			running = Math.max(0, running - 1);
		}

	}

	/**
	 * A login refused because its name is locked out. The login page tells it
	 * apart from a wrong name or password ({@code SecurityConfig}).
	 */
	public static final class LockedOutException extends AuthenticationException {

		private static final long serialVersionUID = 1L;

		LockedOutException() {
			super("Πολλές αποτυχημένες προσπάθειες σύνδεσης.");
		}

	}

}
