package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

/**
 * Starts the application with the create-user profile, as the documented
 * command does. That profile runs without a web server, so it also proves
 * the password encoder is available outside the web application. The
 * password is typed on a stand-in for the console (Task 22a), generated for
 * this run.
 */
@SpringBootTest
@ActiveProfiles("create-user")
@Import({ TestcontainersConfiguration.class, CreateUserProfileTest.TypedOnTheConsole.class })
class CreateUserProfileTest {

	private static final String PASSWORD = "κ" + UUID.randomUUID() + "7";

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@DynamicPropertySource
	static void account(DynamicPropertyRegistry registry) {
		registry.add("user.username", () -> "maria");
		registry.add("user.full-name", () -> "Μαρία Δοκιμαστική");
		registry.add("user.role", () -> "ΔΙΑΧΕΙΡΙΣΤΗΣ");
	}

	@Test
	void createsTheAccountDuringStartup() {
		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();

		assertThat(user.getFullName()).isEqualTo("Μαρία Δοκιμαστική");
		assertThat(user.getRole()).isEqualTo(Role.ΔΙΑΧΕΙΡΙΣΤΗΣ);
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
	}

	/** In place of the real console, which a test run does not have. */
	@TestConfiguration(proxyBeanMethods = false)
	static class TypedOnTheConsole {

		@Bean
		@Primary
		PasswordPrompt typedOnTheConsole() {
			return prompt -> Optional.of(PASSWORD.toCharArray());
		}

	}

}
