package gr.insuranceoffice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import gr.insuranceoffice.service.CustomerService;

/** The customer card (SPEC §7.3). */
@Controller
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping("/customers/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("detail", customerService.findDetail(id));
		return "customer-detail";
	}

}
