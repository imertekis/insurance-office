package gr.insuranceoffice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SecurityConfig {

	// There is no filter chain to build without a web server, as in the
	// import and create-user profiles.
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(requests -> requests
						// The stylesheet is needed to render the login page itself.
						.requestMatchers("/webjars/**").permitAll()
						.anyRequest().authenticated())
				.formLogin(login -> login
						.loginPage("/login")
						.defaultSuccessUrl("/")
						.permitAll())
				.logout(logout -> logout
						.logoutSuccessUrl("/login?logout")
						.permitAll())
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
