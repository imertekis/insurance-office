package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

/**
 * The only way to make an account until there is a user-management screen.
 * Called directly here; under the profile it runs at startup, like the Excel
 * import. The console is a stand-in that types what the test gives it: the
 * passwords are generated on every run, so none of them is anyone's.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CreateUserRunnerTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void startEmpty() {
		appUserRepository.deleteAll();
	}

	@AfterEach
	void leaveEmpty() {
		appUserRepository.deleteAll();
	}

	@Test
	void isNotPartOfTheNormalStartup() {
		assertThat(context.getBeansOfType(CreateUserRunner.class)).isEmpty();
		assertThat(context.getBeansOfType(PasswordPrompt.class)).isEmpty();
	}

	@Test
	void createsAnAdministratorWithABcryptPasswordTypedTwice() {
		String password = generatedPassword();
		Console console = new Console(password, password);

		runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", console).run(null);

		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Μαρία Δοκιμαστική");
		assertThat(user.getRole()).isEqualTo(Role.ΔΙΑΧΕΙΡΙΣΤΗΣ);
		assertThat(user.isActive()).isTrue();
		assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo(password);
		assertThat(passwordEncoder.matches(password, user.getPasswordHash())).isTrue();
		assertThat(console.prompts).containsExactly("Κωδικός για τον χρήστη «maria»: ", "Ξανά ο ίδιος κωδικός: ");
	}

	@Test
	void createsAClerkWhenTheRoleSaysSo() {
		String password = generatedPassword();

		runner("nikos", "Νίκος Δοκιμαστικός", "ΥΠΑΛΛΗΛΟΣ", new Console(password, password)).run(null);

		assertThat(appUserRepository.findByUsername("nikos")).get().extracting(AppUser::getRole)
				.isEqualTo(Role.ΥΠΑΛΛΗΛΟΣ);
	}

	// Doubles as a password reset while there is no screen for it.
	@Test
	void resetsThePasswordOfAnExistingUser() {
		String old = generatedPassword();
		String reset = generatedPassword();
		runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(old, old)).run(null);
		Long id = appUserRepository.findByUsername("maria").orElseThrow().getId();

		runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(reset, reset)).run(null);

		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();
		assertThat(appUserRepository.count()).isEqualTo(1);
		assertThat(user.getId()).isEqualTo(id);
		// The name is kept when the argument is left out.
		assertThat(user.getFullName()).isEqualTo("Μαρία Δοκιμαστική");
		assertThat(passwordEncoder.matches(reset, user.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches(old, user.getPasswordHash())).isFalse();
	}

	// The recovery path: a reset lets a deactivated account in again, and says so.
	@Test
	void reactivatesADeactivatedAccountOnAResetAndSaysSo(CapturedOutput output) {
		String old = generatedPassword();
		String reset = generatedPassword();
		runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(old, old)).run(null);
		assertThat(output.getAll()).doesNotContain("ενεργοποιήθηκε ξανά");
		AppUser deactivated = appUserRepository.findByUsername("maria").orElseThrow();
		deactivated.setActive(false);
		appUserRepository.save(deactivated);

		runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(reset, reset)).run(null);

		assertThat(appUserRepository.findByUsername("maria")).get().extracting(AppUser::isActive).isEqualTo(true);
		assertThat(output.getAll()).containsOnlyOnce("Ο λογαριασμός maria ήταν απενεργοποιημένος και ενεργοποιήθηκε ξανά.");
	}

	// Task 22, decision 8: the command line stays in the shell's history.
	@Test
	void refusesAPasswordOnTheCommandLine() {
		String password = generatedPassword();
		Console console = new Console(password, password);
		CreateUserRunner runner = new CreateUserRunner(appUserRepository, passwordEncoder, console, "maria",
				password, "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ");

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.password")
				.hasMessageNotContaining(password);
		assertThat(console.prompts).isEmpty();
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	void stopsWithoutAConsoleToTypeIn() {
		assertThatThrownBy(() -> runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", prompt -> Optional.empty())
				.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Δεν υπάρχει τερματικό");
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	void refusesTwoDifferentPasswords() {
		assertThatThrownBy(() -> runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ",
				new Console(generatedPassword(), generatedPassword())).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Οι δύο κωδικοί δεν είναι ίδιοι.");
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	void refusesWhatTheRuleRefusesAndSavesNothing() {
		String weak = UUID.randomUUID().toString().substring(0, 7);
		assertThatThrownBy(() -> runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(weak, weak))
				.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PasswordPolicy.TOO_WEAK);
		assertThatThrownBy(() -> runner("maria2024", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ",
				new Console("Maria2024", "Maria2024")).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PasswordPolicy.SAME_AS_USERNAME);
		assertThat(appUserRepository.count()).isZero();
	}

	// A reset must change the password; a refused one leaves the account as it was.
	@Test
	void refusesTheCurrentPasswordOrAWeakOneOnAReset() {
		String current = generatedPassword();
		runner("maria", "Μαρία Δοκιμαστική", "ΥΠΑΛΛΗΛΟΣ", new Console(current, current)).run(null);
		AppUser before = appUserRepository.findByUsername("maria").orElseThrow();

		assertThatThrownBy(() -> runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(current, current)).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PasswordPolicy.SAME_AS_CURRENT);
		assertThatThrownBy(() -> runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console("abc", "abc")).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PasswordPolicy.TOO_WEAK);

		AppUser after = appUserRepository.findByUsername("maria").orElseThrow();
		assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
		assertThat(after.getRole()).isEqualTo(Role.ΥΠΑΛΛΗΛΟΣ);
	}

	// Every argument is checked before the password is asked for.
	@Test
	void explainsAMissingArgumentBeforeAskingForThePassword() {
		Console console = new Console();
		assertThatThrownBy(() -> runner("", "Μαρία", "ΔΙΑΧΕΙΡΙΣΤΗΣ", console).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.username");
		assertThatThrownBy(() -> runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", console).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.full-name");
		assertThat(console.prompts).isEmpty();
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	void refusesAnUnknownRole() {
		Console console = new Console();
		assertThatThrownBy(() -> runner("maria", "Μαρία", "ΑΦΕΝΤΙΚΟ", console).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ΥΠΑΛΛΗΛΟΣ, ΔΙΑΧΕΙΡΙΣΤΗΣ");
		assertThat(console.prompts).isEmpty();
		assertThat(appUserRepository.count()).isZero();
	}

	// Why the rule stops at 72 bytes: the encoder refuses more.
	@Test
	void theEncoderRefusesMoreThanSeventyTwoBytes() {
		assertThatThrownBy(() -> passwordEncoder.encode("α".repeat(36) + "1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void neverWritesThePasswordToTheOutput(CapturedOutput output) {
		String password = generatedPassword();
		String reset = generatedPassword();
		runner("maria", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(password, password)).run(null);
		runner("maria", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ", new Console(reset, reset)).run(null);
		String hash = appUserRepository.findByUsername("maria").orElseThrow().getPasswordHash();

		assertThat(output.getAll()).contains("ο χρήστης maria").doesNotContain(password, reset, hash);
	}

	private CreateUserRunner runner(String username, String fullName, String role, PasswordPrompt console) {
		return new CreateUserRunner(appUserRepository, passwordEncoder, console, username, "", fullName, role);
	}

	// Letters and digits, new on every run.
	private static String generatedPassword() {
		return "κ" + UUID.randomUUID() + "7";
	}

	/** Types the given passwords, one per question, and remembers the questions. */
	private static final class Console implements PasswordPrompt {

		private final Deque<String> typed;

		private final List<String> prompts = new ArrayList<>();

		Console(String... typed) {
			this.typed = new ArrayDeque<>(List.of(typed));
		}

		@Override
		public Optional<char[]> read(String prompt) {
			prompts.add(prompt);
			return Optional.of(typed.pop().toCharArray());
		}

	}

}
