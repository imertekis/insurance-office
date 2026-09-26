package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.repository.CustomerRepository;

/**
 * Task 22a, decision 1: an account whose password was set before the rule,
 * and does not meet it, logs in and is held on «Αλλαγή κωδικού» until the
 * password is changed. The stored hash is written directly, as create-user
 * would now refuse such a password; the passwords are generated on every run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ForcedPasswordChangeTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Seven characters: under the rule.
	private final String weak = UUID.randomUUID().toString().substring(0, 7);

	@BeforeEach
	void createUser() {
		appUserRepository.deleteAll();
		AppUser user = new AppUser();
		user.setUsername("maria");
		user.setPasswordHash(passwordEncoder.encode(weak));
		user.setFullName("Μαρία Δοκιμαστική");
		user.setRole(Role.ΥΠΑΛΛΗΛΟΣ);
		appUserRepository.save(user);
	}

	@AfterEach
	void deleteUsers() {
		appUserRepository.deleteAll();
	}

	@Test
	void logsInWithTheOldPasswordButMarksTheSession() throws Exception {
		mockMvc.perform(formLogin("/login").user("maria").password(weak))
				.andExpect(authenticated().withAuthentication(authentication -> assertThat(
						authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
						.contains("ROLE_ΥΠΑΛΛΗΛΟΣ", ForcedPasswordChange.AUTHORITY)))
				.andExpect(redirectedUrl("/"));
	}

	// Pages, the suggestions, and a form sent from another tab.
	@Test
	void leadsEveryPageToTheChangeUntilItIsDone() throws Exception {
		MockHttpSession session = logIn(weak);
		long customers = customerRepository.count();

		for (String page : List.of("/", "/customers", "/vehicles/new", "/search?q=abc", "/policies")) {
			mockMvc.perform(get(page).session(session)).andExpect(redirectedUrl("/account/password"));
		}
		mockMvc.perform(get("/search/suggestions").param("q", "abc").accept(MediaType.APPLICATION_JSON)
				.session(session)).andExpect(redirectedUrl("/account/password"));
		mockMvc.perform(post("/customers").session(session).with(csrf()).param("lastName", "Δοκιμαστικός")
				.param("entityType", "INDIVIDUAL")).andExpect(redirectedUrl("/account/password"));
		assertThat(customerRepository.count()).isEqualTo(customers);

		String page = mockMvc.perform(get("/account/password").session(session)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(page).contains("Ο κωδικός σας δεν πληροί πλέον τους κανόνες· ορίστε νέο για να συνεχίσετε.")
				.doesNotContain(">Άκυρο</a>");
	}

	// Whatever the page needs to show, and the way out.
	@Test
	void stillServesTheStylesheetsAndLogsOut() throws Exception {
		MockHttpSession session = logIn(weak);

		mockMvc.perform(get("/css/app.css").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/js/app.js").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css").session(session))
				.andExpect(status().isOk());
		mockMvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	void letsEverythingThroughOnceThePasswordIsChanged() throws Exception {
		MockHttpSession session = logIn(weak);
		String changed = "κ" + UUID.randomUUID() + "7";

		mockMvc.perform(post("/account/password").session(session).with(csrf()).param("currentPassword", weak)
				.param("newPassword", changed).param("newPasswordAgain", changed))
				.andExpect(redirectedUrl("/"));

		mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/customers").session(session)).andExpect(status().isOk());
		// And the next login is an ordinary one.
		mockMvc.perform(formLogin("/login").user("maria").password(changed))
				.andExpect(authenticated().withAuthentication(authentication -> assertThat(
						authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
						.doesNotContain(ForcedPasswordChange.AUTHORITY)));
	}

	// A refused change keeps the hold.
	@Test
	void keepsTheHoldAfterARefusedChange() throws Exception {
		MockHttpSession session = logIn(weak);

		mockMvc.perform(post("/account/password").session(session).with(csrf()).param("currentPassword", weak)
				.param("newPassword", "abc").param("newPasswordAgain", "abc"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/").session(session)).andExpect(redirectedUrl("/account/password"));
	}

	// A password that meets the rule is an ordinary login.
	@Test
	void leavesAPasswordThatMeetsTheRuleAlone() throws Exception {
		String strong = "κ" + UUID.randomUUID() + "7";
		AppUser user = appUserRepository.findByUsername("maria").orElseThrow();
		user.setPasswordHash(passwordEncoder.encode(strong));
		appUserRepository.save(user);

		MockHttpSession session = logIn(strong);

		mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
	}

	private MockHttpSession logIn(String password) throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/login").user("maria").password(password))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

}
