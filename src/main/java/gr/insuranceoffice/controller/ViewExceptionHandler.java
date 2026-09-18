package gr.insuranceoffice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import gr.insuranceoffice.service.NotFoundException;

/**
 * Shows a record that is gone as a page rather than an error trace. The JSON
 * endpoints keep their problem responses in {@link GlobalExceptionHandler}.
 */
// Listed one by one: @RestController is itself a @Controller, and the JSON
// endpoints must keep answering with problem details.
@ControllerAdvice(assignableTypes = { DashboardController.class, SearchController.class, CustomerController.class,
		VehicleController.class, OwnershipController.class })
public class ViewExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String notFound(NotFoundException exception, Model model) {
		model.addAttribute("message", exception.getMessage());
		return "not-found";
	}

}
