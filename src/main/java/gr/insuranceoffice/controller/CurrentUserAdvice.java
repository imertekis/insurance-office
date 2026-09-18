package gr.insuranceoffice.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import gr.insuranceoffice.security.CurrentUser;

/** Puts the logged-in user's name in the header of every page (SPEC §2). */
@ControllerAdvice(assignableTypes = { DashboardController.class, SearchController.class, CustomerController.class,
		VehicleController.class })
public class CurrentUserAdvice {

	@ModelAttribute("currentUser")
	String currentUser() {
		return CurrentUser.displayName().orElse(null);
	}

}
