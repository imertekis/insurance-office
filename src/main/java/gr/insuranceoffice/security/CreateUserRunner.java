package gr.insuranceoffice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 *     -Dspring-boot.run.arguments="--user.username=maria --user.password=... --user.full-name=Μαρία --user.role=ΔΙΑΧΕΙΡΙΣΤΗΣ"
 * </pre>
 *
 * Exists only under the {@code create-user} profile, so no normal start can
 * touch accounts. The password is hashed with bcrypt and never logged.
 */
@Component
@Profile("create-user")
public class CreateUserRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CreateUserRunner.class);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String password;
	private final String fullName;
	private final String role;

	public CreateUserRunner(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
			@Value("${user.username:}") String username,
			@Value("${user.password:}") String password,
			@Value("${user.full-name:}") String fullName,
			@Value("${user.role:ΔΙΑΧΕΙΡΙΣΤΗΣ}") String role) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.password = password;
		this.fullName = fullName;
		this.role = role;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		required("user.username", username);
		required("user.password", password);
		Role parsedRole = parseRole();

		AppUser user = appUserRepository.findByUsername(username.strip()).orElseGet(AppUser::new);
		boolean existed = user.getId() != null;
		user.setUsername(username.strip());
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(parsedRole);
		user.setActive(true);
		if (!fullName.isBlank() || !existed) {
			required("user.full-name", fullName);
			user.setFullName(fullName.strip());
		}
		appUserRepository.save(user);

		log.info("{} ο χρήστης {} ({})", existed ? "Ενημερώθηκε" : "Δημιουργήθηκε", user.getUsername(),
				parsedRole);
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
					+ "--user.password=<κωδικός> --user.full-name=<ονοματεπώνυμο> [--user.role=ΥΠΑΛΛΗΛΟΣ]");
		}
	}

}
