package gr.insuranceoffice.config;

import java.time.Clock;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;

import gr.insuranceoffice.security.ForcedPasswordChangeFilter;
import gr.insuranceoffice.security.LoginAttempts;
import gr.insuranceoffice.security.PasswordCheckingAuthenticationProvider;
import gr.insuranceoffice.security.PasswordPrompt;

/**
 * Session-based login for the whole application (SPEC §2): identifying the
 * user is required even on the office LAN, since the VPN only decides which
 * machines reach the application, not who is using it.
 * <p>
 * The password encoder is always defined: the create-user profile needs it
 * to hash a password although it runs without a web server.
 */
@Configuration
// Deleting is checked on the services themselves (@PreAuthorize), so no
// path to a delete can forget it (Task 11e).
@EnableMethodSecurity
public class SecurityConfig {

	// There is no filter chain to build without a web server, as in the
	// import and create-user profiles.
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(requests -> requests
						// The login page itself needs the stylesheets, ours too (Task
						// 16d-1), and the theme script (Task 16c) to follow the OS
						// while it is open. Public, static files only.
						.requestMatchers("/webjars/**", "/js/**", "/css/**").permitAll()
						// Its tab icon too (Task 16d-2): by exact path, not "/*", so
						// nothing else put in static/ one day is public by accident.
						.requestMatchers("/favicon.ico", "/favicon.svg").permitAll()
						// The login page with any of its messages (?error, ?locked,
						// ?logout). formLogin's permitAll() covers the failure address
						// only when it sets it itself, by its exact text, and a handler
						// of our own leaves it out (Task 22b).
						.requestMatchers("/login").permitAll()
						.anyRequest().authenticated())
				.formLogin(login -> login
						.loginPage("/login")
						.defaultSuccessUrl("/")
						.failureHandler(loginFailureHandler())
						.permitAll())
				.logout(logout -> logout
						.logoutSuccessUrl("/login?logout")
						.permitAll())
				// A logged-in clerk who tries to delete gets a page saying so,
				// with status 403, not the login page.
				.exceptionHandling(exceptions -> exceptions.accessDeniedPage("/access-denied"))
				// Task 22a: a password that no longer meets the rule is changed
				// before anything else. After authorization: a page that needs
				// a login has one by then. Not a bean, which Spring Boot would
				// also register as a filter of its own, outside this chain.
				.addFilterAfter(new ForcedPasswordChangeFilter(), AuthorizationFilter.class)
				.build();
	}

	// Task 22b: a name locked out after too many failed logins is told so;
	// any other failure gets the one message that names neither half.
	private static AuthenticationFailureHandler loginFailureHandler() {
		ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
		handler.setDefaultFailureUrl("/login?error");
		handler.setExceptionMappings(Map.of(LoginAttempts.LockedOutException.class.getName(), "/login?locked"));
		return handler;
	}

	// The one way to log in, so Spring Security uses it instead of building
	// its own from the UserDetailsService (Task 22a: it also checks the
	// password just typed against the rule). Task 22b: before the password,
	// it asks LoginAttempts whether the name is locked out.
	@Bean
	AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, LoginAttempts loginAttempts) {
		return new PasswordCheckingAuthenticationProvider(userDetailsService, passwordEncoder, loginAttempts);
	}

	// The time of the login lockout (Task 22b), a bean so that tests can move
	// it on instead of waiting five minutes.
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

	// Task 22a: create-user asks for the password on the console, never on
	// the command line.
	@Bean
	@Profile("create-user")
	PasswordPrompt passwordPrompt() {
		return PasswordPrompt.console();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
