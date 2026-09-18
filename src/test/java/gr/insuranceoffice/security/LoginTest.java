package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

/** SPEC §2: the application is locked, whoever is on the LAN. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void createUser() {
		appUserRepository.deleteAll();
		user("maria", "μυστικό7", Role.ΥΠΑΛΛΗΛΟΣ, true);
	}

	@AfterEach
	void deleteUsers() {
		appUserRepository.deleteAll();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "/", "/search", "/customers/1", "/vehicles/1", "/api/customers" })
	void sendsAnAnonymousVisitorToTheLoginPage(String url) throws Exception {
		mockMvc.perform(get(url))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void showsTheLoginPageWithoutLoggingIn() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Όνομα χρήστη")));
	}

	// The stylesheet has to load, or the login page is unreadable.
	@Test
	void servesTheStylesheetWithoutLoggingIn() throws Exception {
		mockMvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css")).andExpect(status().isOk());
	}

	@Test
	void logsInWithThePasswordFromAppUser() throws Exception {
		mockMvc.perform(formLogin("/login").user("maria").password("μυστικό7"))
				.andExpect(authenticated().withUsername("maria").withRoles("ΥΠΑΛΛΗΛΟΣ"))
				.andExpect(redirectedUrl("/"));
	}

	// The principal carries the app_user id, which is what AuditListener
	// writes to audit_log.user_id (ARCHITECTURE §6).
	@Test
	void logsInAsAPrincipalTheAuditLogCanRecord() throws Exception {
		AppUser stored = appUserRepository.findByUsername("maria").orElseThrow();

		mockMvc.perform(formLogin("/login").user("maria").password("μυστικό7"))
				.andExpect(authenticated().withAuthentication(authentication -> assertThat(authentication.getPrincipal())
						.isInstanceOfSatisfying(AppUserDetails.class, user -> {
							assertThat(user.getId()).isEqualTo(stored.getId());
							assertThat(user.getFullName()).isEqualTo("Δοκιμαστικός Χρήστης");
						})));
	}

	@Test
	void refusesAWrongPasswordOrAnUnknownUser() throws Exception {
		mockMvc.perform(formLogin("/login").user("maria").password("λάθος"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
		mockMvc.perform(formLogin("/login").user("κανείς").password("μυστικό7"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void refusesADeactivatedUser() throws Exception {
		user("nikos", "μυστικό7", Role.ΥΠΑΛΛΗΛΟΣ, false);

		mockMvc.perform(formLogin("/login").user("nikos").password("μυστικό7"))
				.andExpect(unauthenticated());
	}

	@Test
	void showsWhoIsLoggedInAndTheWayOut() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc
				.perform(formLogin("/login").user("maria").password("μυστικό7"))
				.andReturn().getRequest().getSession(false);

		String html = mockMvc.perform(get("/").session(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("Δοκιμαστικός Χρήστης", "Αποσύνδεση", "action=\"/logout\"");
	}

	@Test
	void logsOutAgain() throws Exception {
		mockMvc.perform(logout())
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?logout"));
	}

	// Passwords are stored hashed, never in the clear (SPEC §12).
	@Test
	void storesThePasswordAsABcryptHash() {
		String hash = appUserRepository.findByUsername("maria").orElseThrow().getPasswordHash();

		assertThat(hash).startsWith("$2a$").isNotEqualTo("μυστικό7");
		assertThat(passwordEncoder.matches("μυστικό7", hash)).isTrue();
	}

	private void user(String username, String password, Role role, boolean active) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setFullName("Δοκιμαστικός Χρήστης");
		user.setRole(role);
		user.setActive(active);
		appUserRepository.save(user);
	}

}
