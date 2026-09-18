package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * import.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
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
	}

	@Test
	void createsAnAdministratorWithABcryptPassword() {
		runner("maria", "μυστικό7", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null);

		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Μαρία Δοκιμαστική");
		assertThat(user.getRole()).isEqualTo(Role.ΔΙΑΧΕΙΡΙΣΤΗΣ);
		assertThat(user.isActive()).isTrue();
		assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo("μυστικό7");
		assertThat(passwordEncoder.matches("μυστικό7", user.getPasswordHash())).isTrue();
	}

	@Test
	void createsAClerkWhenTheRoleSaysSo() {
		runner("nikos", "μυστικό7", "Νίκος Δοκιμαστικός", "ΥΠΑΛΛΗΛΟΣ").run(null);

		assertThat(appUserRepository.findByUsername("nikos")).get().extracting(AppUser::getRole)
				.isEqualTo(Role.ΥΠΑΛΛΗΛΟΣ);
	}

	// Doubles as a password reset while there is no screen for it.
	@Test
	void resetsThePasswordOfAnExistingUser() {
		runner("maria", "παλιός7", "Μαρία Δοκιμαστική", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null);
		Long id = appUserRepository.findByUsername("maria").orElseThrow().getId();

		runner("maria", "νέος7", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null);

		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();
		assertThat(appUserRepository.count()).isEqualTo(1);
		assertThat(user.getId()).isEqualTo(id);
		// The name is kept when the argument is left out.
		assertThat(user.getFullName()).isEqualTo("Μαρία Δοκιμαστική");
		assertThat(passwordEncoder.matches("νέος7", user.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("παλιός7", user.getPasswordHash())).isFalse();
	}

	@Test
	void explainsAMissingArgument() {
		assertThatThrownBy(() -> runner("", "μυστικό7", "Μαρία", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.username");
		assertThatThrownBy(() -> runner("maria", "", "Μαρία", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.password");
		assertThatThrownBy(() -> runner("maria", "μυστικό7", "", "ΔΙΑΧΕΙΡΙΣΤΗΣ").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("--user.full-name");
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	void refusesAnUnknownRole() {
		assertThatThrownBy(() -> runner("maria", "μυστικό7", "Μαρία", "ΑΦΕΝΤΙΚΟ").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ΥΠΑΛΛΗΛΟΣ, ΔΙΑΧΕΙΡΙΣΤΗΣ");
		assertThat(appUserRepository.count()).isZero();
	}

	private CreateUserRunner runner(String username, String password, String fullName, String role) {
		return new CreateUserRunner(appUserRepository, passwordEncoder, username, password, fullName, role);
	}

}
