package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.security.PasswordPolicy;

/**
 * Task 22a: «Αλλαγή κωδικού», through a real login: the page changes the
 * password of the account the session belongs to. The passwords are
 * generated on every run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountPasswordTest {

	private static final Pattern PASSWORD_INPUT = Pattern.compile("<input[^>]*type=\"password\"[^>]*>");

	private static final Pattern PASSWORD_INPUT_WITH_VALUE = Pattern.compile("<input[^>]*type=\"password\"[^>]*value=");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String password;

	private MockHttpSession session;

	@BeforeEach
	void logIn() throws Exception {
		appUserRepository.deleteAll();
		password = generatedPassword();
		AppUser user = new AppUser();
		user.setUsername("maria");
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setFullName("Μαρία Δοκιμαστική");
		user.setRole(Role.ΥΠΑΛΛΗΛΟΣ);
		appUserRepository.save(user);
		session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("maria").password(password))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	@AfterEach
	void deleteUsers() {
		appUserRepository.deleteAll();
	}

	// Above «Αποσύνδεση», on every page with the header.
	@Test
	void offersTheChangeInTheUserMenuOfEveryPage() throws Exception {
		for (String page : List.of("/", "/customers", "/vehicles/new", "/search", "/account/password")) {
			String html = html(get(page));
			String userMenu = html.substring(html.indexOf("id=\"header-user\""), html.indexOf("</nav>"));
			assertThat(userMenu).as(page).contains("href=\"/account/password\"", ">Αλλαγή κωδικού</a>");
			assertThat(userMenu.indexOf("Αλλαγή κωδικού")).as(page).isLessThan(userMenu.indexOf("Αποσύνδεση"));
		}
	}

	@Test
	void asksForTheCurrentPasswordAndTheNewOneTwice() throws Exception {
		String html = html(get("/account/password"));

		assertThat(html).contains("id=\"currentPassword\"", "autocomplete=\"current-password\"",
				"id=\"newPassword\"", "id=\"newPasswordAgain\"", "autocomplete=\"new-password\"", ">Άκυρο</a>")
				.doesNotContain("δεν πληροί πλέον τους κανόνες");
		assertThat(PASSWORD_INPUT.matcher(html).results()).hasSize(3);
		assertThat(PASSWORD_INPUT_WITH_VALUE.matcher(html).find()).isFalse();
	}

	@Test
	void changesThePasswordOfTheLoggedInUser() throws Exception {
		String changed = generatedPassword();

		mockMvc.perform(change(password, changed, changed))
				.andExpect(redirectedUrl("/"))
				.andExpect(flash().attribute("notice", "Ο κωδικός άλλαξε."));

		mockMvc.perform(formLogin("/login").user("maria").password(changed)).andExpect(authenticated());
		mockMvc.perform(formLogin("/login").user("maria").password(password)).andExpect(unauthenticated());
		// The session goes on.
		mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
	}

	@Test
	void refusesAWrongCurrentPassword() throws Exception {
		String changed = generatedPassword();
		String wrong = generatedPassword();

		String html = refused(change(wrong, changed, changed));

		assertThat(html).contains("Ο τρέχων κωδικός δεν είναι σωστός.").doesNotContain(wrong, changed);
		assertUnchanged();
	}

	@Test
	void refusesTwoDifferentNewPasswords() throws Exception {
		String changed = generatedPassword();
		String other = generatedPassword();

		String html = refused(change(password, changed, other));

		assertThat(html).contains("Οι δύο νέοι κωδικοί δεν είναι ίδιοι.").doesNotContain(password, changed, other);
		assertUnchanged();
	}

	@Test
	void refusesWhatTheRuleRefuses() throws Exception {
		String weak = UUID.randomUUID().toString().substring(0, 7);
		assertThat(refused(change(password, weak, weak))).contains(PasswordPolicy.TOO_WEAK).doesNotContain(weak);

		String tooLong = "α".repeat(36) + "1";
		assertThat(refused(change(password, tooLong, tooLong))).contains(PasswordPolicy.TOO_LONG);

		// The username a password may not be, whatever its case.
		appUserRepository.findByUsername("maria").ifPresent(user -> {
			user.setUsername("maria2024");
			appUserRepository.save(user);
		});
		assertThat(refused(change(password, "MARIA2024", "MARIA2024"))).contains(PasswordPolicy.SAME_AS_USERNAME);

		assertThat(refused(change(password, password, password))).contains(PasswordPolicy.SAME_AS_CURRENT);
		assertThat(passwordEncoder.matches(password,
				appUserRepository.findByUsername("maria2024").orElseThrow().getPasswordHash())).isTrue();
	}

	@Test
	void refusesAChangeWithoutTheFormsToken() throws Exception {
		String changed = generatedPassword();

		mockMvc.perform(post("/account/password").session(session).param("currentPassword", password)
				.param("newPassword", changed).param("newPasswordAgain", changed))
				.andExpect(status().isForbidden());

		assertUnchanged();
	}

	@Test
	void sendsAnAnonymousVisitorToTheLoginPage() throws Exception {
		mockMvc.perform(get("/account/password")).andExpect(redirectedUrl("/login"));
	}

	// No password or hash in the output of the application or in audit_log.
	@Test
	void writesNeitherPasswordNorHashAnywhere(CapturedOutput output) throws Exception {
		String changed = generatedPassword();
		String wrong = generatedPassword();
		refused(change(wrong, changed, changed));
		mockMvc.perform(change(password, changed, changed)).andExpect(redirectedUrl("/"));
		String hash = appUserRepository.findByUsername("maria").orElseThrow().getPasswordHash();

		assertThat(output.getAll()).doesNotContain(password, changed, wrong, hash);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from audit_log
				where coalesce(old_values::text, '') || coalesce(new_values::text, '') like ?
				or entity_type = 'AppUser'
				""", Long.class, "%" + hash.substring(7) + "%")).isZero();
	}

	private MockHttpServletRequestBuilder change(String current, String changed, String again) {
		return post("/account/password").session(session).with(csrf()).param("currentPassword", current)
				.param("newPassword", changed).param("newPasswordAgain", again);
	}

	// The form again, with its message, and never a password written back into it.
	private String refused(MockHttpServletRequestBuilder request) throws Exception {
		String html = mockMvc.perform(request).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("is-invalid");
		assertThat(PASSWORD_INPUT_WITH_VALUE.matcher(html).find()).isFalse();
		return html;
	}

	private void assertUnchanged() {
		assertThat(passwordEncoder.matches(password,
				appUserRepository.findByUsername("maria").orElseThrow().getPasswordHash())).isTrue();
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	// Letters and digits, new on every run.
	private static String generatedPassword() {
		return "κ" + UUID.randomUUID() + "7";
	}

}
