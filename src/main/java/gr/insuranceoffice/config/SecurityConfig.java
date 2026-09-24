package gr.insuranceoffice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
						.anyRequest().authenticated())
				.formLogin(login -> login
						.loginPage("/login")
						.defaultSuccessUrl("/")
						.permitAll())
				.logout(logout -> logout
						.logoutSuccessUrl("/login?logout")
						.permitAll())
				// A logged-in clerk who tries to delete gets a page saying so,
				// with status 403, not the login page.
				.exceptionHandling(exceptions -> exceptions.accessDeniedPage("/access-denied"))
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
