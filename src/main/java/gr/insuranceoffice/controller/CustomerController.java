package gr.insuranceoffice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.service.CustomerService;

/**
 * Temporary REST endpoint proving the infrastructure end to end.
 * Replaced by Thymeleaf views in Task 8.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping
	public List<CustomerDto> findAll() {
		return customerService.findAll();
	}

}
