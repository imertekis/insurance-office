package gr.insuranceoffice.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's time: "today" for the services and the demo's data, and
 * the time of the login lockout (Task 22b). A bean, so that tests can fix it
 * or move it on instead of depending on the day they run.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
