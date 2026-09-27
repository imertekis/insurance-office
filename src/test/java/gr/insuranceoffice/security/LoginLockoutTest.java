package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

/**
 * Task 22b, through the login form: five failed logins lock a name out for
 * five minutes, whether an account has it or not, and the page does not
 * tell the two apart. The clock is moved by hand, and the passwords are
 * generated on every run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, LoginLockoutTest.Time.class })
@ExtendWith(OutputCaptureExtension.class)
class LoginLockoutTest {

	private static final String LOCKED_OUT =
			"Πολλές αποτυχημένες προσπάθειες σύνδεσης. Δοκιμάστε ξανά σε λίγα λεπτά.";

	private static final String WRONG_NAME_OR_PASSWORD = "Λάθος όνομα χρήστη ή κωδικός.";

	@TestConfiguration(proxyBeanMethods = false)
	static class Time {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private MutableClock clock;

	// Spied on to show that no password is checked while a name is locked out.
	@MockitoSpyBean
	private PasswordEncoder passwordEncoder;

	// The attempts live as long as the application does, so every test has
	// names of its own.
	private final String maria = "maria-" + suffix();

	private final String nikos = "nikos-" + suffix();

	private final String nobody = "nobody-" + suffix();

	private final String mariaPassword = generatedPassword();

	private final String nikosPassword = generatedPassword();

	@BeforeEach
	void createUsers() {
		user(maria, mariaPassword);
		user(nikos, nikosPassword);
	}

	@AfterEach
	void deleteUsers() {
		appUserRepository.deleteAll();
	}

	@Test
	void refusesTheSixthLoginEvenWithTheRightPassword() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(page(logIn(maria, generatedPassword()), "/login?error")).contains(WRONG_NAME_OR_PASSWORD);
		}

		MvcResult sixth = logIn(maria, mariaPassword);

		assertThat(page(sixth, "/login?locked")).contains(LOCKED_OUT).doesNotContain(WRONG_NAME_OR_PASSWORD);
	}

	// Decision 4: the same answers, one by one, and the same page.
	@Test
	void locksANameWithoutAnAccountTheSameWay() throws Exception {
		List<String> withAccount = new ArrayList<>();
		List<String> withoutAccount = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			withAccount.add(answer(logIn(maria, generatedPassword())));
			withoutAccount.add(answer(logIn(nobody, generatedPassword())));
		}
		MvcResult sixthWithAccount = logIn(maria, mariaPassword);
		MvcResult sixthWithoutAccount = logIn(nobody, mariaPassword);
		withAccount.add(answer(sixthWithAccount));
		withoutAccount.add(answer(sixthWithoutAccount));

		assertThat(withoutAccount).isEqualTo(withAccount);
		assertThat(withAccount).last().asString().startsWith("302 /login?locked");
		assertThat(withoutCsrfToken(page(sixthWithoutAccount, "/login?locked")))
				.isEqualTo(withoutCsrfToken(page(sixthWithAccount, "/login?locked")))
				.contains(LOCKED_OUT)
				.doesNotContain(maria, nobody);
	}

	// So the answer takes the same time for a name with an account or without.
	@Test
	void checksNoPasswordWhileTheNameIsLockedOut() throws Exception {
		for (int i = 0; i < 5; i++) {
			logIn(maria, generatedPassword());
			logIn(nobody, generatedPassword());
		}
		clearInvocations(passwordEncoder);

		logIn(maria, mariaPassword);
		logIn(nobody, mariaPassword);

		verify(passwordEncoder, never()).matches(any(), any());
	}

	@Test
	void letsAnotherUserLogIn() throws Exception {
		lockOut(maria);

		mockMvc.perform(formLogin("/login").user(nikos).password(nikosPassword))
				.andExpect(authenticated().withUsername(nikos))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void letsTheNameInAgainFiveMinutesLater() throws Exception {
		lockOut(maria);

		clock.advance(Duration.ofMinutes(4).plusSeconds(59));
		mockMvc.perform(formLogin("/login").user(maria).password(mariaPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?locked"));

		clock.advance(Duration.ofSeconds(1));
		mockMvc.perform(formLogin("/login").user(maria).password(mariaPassword))
				.andExpect(authenticated().withUsername(maria))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void startsTheCountOverAfterASuccess() throws Exception {
		for (int i = 0; i < 4; i++) {
			logIn(maria, generatedPassword());
		}
		mockMvc.perform(formLogin("/login").user(maria).password(mariaPassword)).andExpect(authenticated());
		for (int i = 0; i < 4; i++) {
			mockMvc.perform(formLogin("/login").user(maria).password(generatedPassword()))
					.andExpect(redirectedUrl("/login?error"));
		}

		mockMvc.perform(formLogin("/login").user(maria).password(mariaPassword)).andExpect(authenticated());
	}

	// Decision 5: the name of an account, never a name without one; once
	// per lockout, with the time.
	@Test
	void logsEachLockoutWithTheNameOnlyOfAnAccount(CapturedOutput output) throws Exception {
		String time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
				.format(clock.instant().atZone(MutableClock.ATHENS));

		lockOut(maria);
		lockOut(nobody);
		logIn(maria, mariaPassword);
		logIn(nobody, mariaPassword);

		assertThat(output.getAll())
				.containsOnlyOnce("Κλείδωμα σύνδεσης για τον λογαριασμό «" + maria + "» μετά από 5 αποτυχημένες "
						+ "προσπάθειες, στις " + time + ", για 5 λεπτά.")
				.containsOnlyOnce("Κλείδωμα σύνδεσης για άγνωστο όνομα μετά από 5 αποτυχημένες προσπάθειες, στις "
						+ time + ", για 5 λεπτά.")
				.doesNotContain(nobody, mariaPassword);
	}

	// The failure has a handler of its own since Task 22b, so the page is
	// public by its path, whatever message it carries.
	@Test
	void showsEachMessageOfTheLoginPageWithoutALogin() throws Exception {
		assertThat(html("/login?error")).contains(WRONG_NAME_OR_PASSWORD).doesNotContain(LOCKED_OUT);
		assertThat(html("/login?locked")).contains(LOCKED_OUT).doesNotContain(WRONG_NAME_OR_PASSWORD);
		assertThat(html("/login?logout")).contains("Αποσυνδεθήκατε.");
		assertThat(html("/login")).doesNotContain(WRONG_NAME_OR_PASSWORD, LOCKED_OUT);
	}

	private void lockOut(String username) throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(formLogin("/login").user(username).password(generatedPassword()))
					.andExpect(redirectedUrl("/login?error"));
		}
	}

	private MvcResult logIn(String username, String password) throws Exception {
		return mockMvc.perform(formLogin("/login").user(username).password(password))
				.andExpect(unauthenticated())
				.andReturn();
	}

	// Everything the browser gets back from the login form.
	private static String answer(MvcResult result) throws Exception {
		MockHttpServletResponse response = result.getResponse();
		return response.getStatus() + " " + response.getRedirectedUrl() + " "
				+ response.getHeaderNames().stream().map(name -> name + "=" + response.getHeaderValues(name)).toList()
				+ " " + response.getContentAsString();
	}

	// The page the login form leads to, in the session it left behind if any.
	private String page(MvcResult result, String expectedUrl) throws Exception {
		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(expectedUrl);
		MockHttpServletRequestBuilder request = get(expectedUrl);
		if (result.getRequest().getSession(false) instanceof MockHttpSession session) {
			request.session(session);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String html(String url) throws Exception {
		return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String withoutCsrfToken(String html) {
		return html.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"");
	}

	private void user(String username, String password) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setFullName("Δοκιμαστικός Χρήστης");
		user.setRole(Role.ΥΠΑΛΛΗΛΟΣ);
		appUserRepository.save(user);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	// Letters and digits, new on every run.
	private static String generatedPassword() {
		return "κ" + UUID.randomUUID() + "7";
	}

}
