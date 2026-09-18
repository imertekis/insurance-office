package gr.insuranceoffice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Pages with nothing to fetch, so no controller of their own. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Spring Security only handles the POST; the page itself is ours.
		registry.addViewController("/login").setViewName("login");
	}

}
