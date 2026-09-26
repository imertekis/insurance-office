package gr.insuranceoffice.security;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

/**
 * Creates a user, or resets an existing one's password and role, then lets
 * the application exit. There is no user management screen yet, and this is
 * how the first ΔΙΑΧΕΙΡΙΣΤΗΣ is made:
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=create-user \
 *     -Dspring-boot.run.arguments="--user.username=maria --user.full-name=Μαρία --user.role=ΔΙΑΧΕΙΡΙΣΤΗΣ"
 * </pre>
 *
 * The password is asked for on the console, twice, without showing it, and
 * never taken from the command line (Task 22a). It must meet
 * {@link PasswordPolicy}, and a reset must change it. It is hashed with
 * bcrypt and never logged.
 * <p>
 * Exists only under the {@code create-user} profile, so no normal start can
 * touch accounts.
 */
@Component
@Profile("create-user")
public class CreateUserRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CreateUserRunner.class);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPrompt passwordPrompt;
	private final String username;
	private final String passwordArgument;
	private final String fullName;
	private final String role;

	public CreateUserRunner(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
			PasswordPrompt passwordPrompt,
			@Value("${user.username:}") String username,
			// Read only to refuse it.
			@Value("${user.password:}") String passwordArgument,
			@Value("${user.full-name:}") String fullName,
			@Value("${user.role:ΔΙΑΧΕΙΡΙΣΤΗΣ}") String role) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordPrompt = passwordPrompt;
		this.username = username;
		this.passwordArgument = passwordArgument;
		this.fullName = fullName;
		this.role = role;
	}

	// Not @Transactional: no connection is held while someone types. The
	// lookup and the save are a transaction each.
	@Override
	public void run(ApplicationArguments args) {
		if (!passwordArgument.isEmpty()) {
			throw new IllegalStateException("Ο κωδικός δεν δίνεται στη γραμμή εντολής, γιατί μένει στο ιστορικό "
					+ "του shell. Αφαιρέστε το --user.password: ο κωδικός θα ζητηθεί χωρίς να φαίνεται.");
		}
		// Every argument first, so nobody types a password only to hear that
		// one was missing.
		required("user.username", username);
		Role parsedRole = parseRole();
		AppUser user = appUserRepository.findByUsername(username.strip()).orElseGet(AppUser::new);
		boolean existed = user.getId() != null;
		if (!fullName.isBlank() || !existed) {
			required("user.full-name", fullName);
		}

		String password = newPassword(username.strip(), existed ? user.getPasswordHash() : null);
		user.setUsername(username.strip());
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(parsedRole);
		user.setActive(true);
		if (!fullName.isBlank()) {
			user.setFullName(fullName.strip());
		}
		appUserRepository.save(user);

		log.info("{} ο χρήστης {} ({})", existed ? "Ενημερώθηκε" : "Δημιουργήθηκε", user.getUsername(),
				parsedRole);
	}

	/**
	 * The password, typed twice on the console and checked against the rule.
	 *
	 * @param currentHash the stored hash on a reset, which the new password
	 *            must not match; null for a new account
	 */
	private String newPassword(String account, String currentHash) {
		char[] first = typed("Κωδικός για τον χρήστη «" + account + "»: ");
		char[] second = typed("Ξανά ο ίδιος κωδικός: ");
		try {
			if (!Arrays.equals(first, second)) {
				throw new IllegalStateException("Οι δύο κωδικοί δεν είναι ίδιοι. Δεν άλλαξε τίποτα.");
			}
			String password = new String(first);
			PasswordPolicy.check(password, account).ifPresent(problem -> {
				throw new IllegalStateException(problem + " Δεν άλλαξε τίποτα.");
			});
			if (currentHash != null && passwordEncoder.matches(password, currentHash)) {
				throw new IllegalStateException(PasswordPolicy.SAME_AS_CURRENT + " Δεν άλλαξε τίποτα.");
			}
			return password;
		} finally {
			Arrays.fill(first, '\0');
			Arrays.fill(second, '\0');
		}
	}

	private char[] typed(String prompt) {
		return passwordPrompt.read(prompt).orElseThrow(() -> new IllegalStateException("Δεν υπάρχει τερματικό "
				+ "για να γραφτεί ο κωδικός χωρίς να φαίνεται. Τρέξτε την εντολή σε τερματικό, χωρίς να "
				+ "ανακατευθύνετε την είσοδο ή την έξοδό της."));
	}

	private Role parseRole() {
		try {
			return Role.valueOf(role.strip());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(
					"Άγνωστος ρόλος «" + role + "». Επιτρέπονται: ΥΠΑΛΛΗΛΟΣ, ΔΙΑΧΕΙΡΙΣΤΗΣ", e);
		}
	}

	private static void required(String property, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Λείπει το --" + property + ". Χρήση: --user.username=<όνομα> "
					+ "--user.full-name=<ονοματεπώνυμο> [--user.role=ΥΠΑΛΛΗΛΟΣ]· ο κωδικός ζητείται μετά.");
		}
	}

}
