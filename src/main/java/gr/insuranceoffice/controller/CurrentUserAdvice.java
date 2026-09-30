package gr.insuranceoffice.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import gr.insuranceoffice.security.CurrentUser;

/**
 * Puts the logged-in user in every page (SPEC §2): the name for the header,
 * and whether to offer the delete buttons (Task 11e). Hiding a button is a
 * courtesy; the services refuse the delete itself.
 */
// The controllers are listed by hand, and nothing checks the list: a new
// controller left out renders its pages without the user's name in the
// header and without delete buttons, and no test fails (NOTES, «Controller
// advice lists»).
@ControllerAdvice(assignableTypes = { DashboardController.class, SearchController.class, CustomerController.class,
		VehicleController.class, OwnershipController.class, PolicyController.class, AccountController.class })
public class CurrentUserAdvice {

	@ModelAttribute("currentUser")
	String currentUser() {
		return CurrentUser.displayName().orElse(null);
	}

	@ModelAttribute("canDelete")
	boolean canDelete() {
		return CurrentUser.isAdministrator();
	}

}
