package gr.insuranceoffice.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.SavedCustomerDto;
import gr.insuranceoffice.service.CustomerService;

/**
 * Temporary JSON endpoints for saving a customer, so that validation and the
 * 409 on a concurrent change can be reached over HTTP before the Task 11
 * forms replace them. The Task 1 listing is gone: the pages are Thymeleaf
 * from Task 8 on. Errors are mapped by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@PostMapping
	public ResponseEntity<SavedCustomerDto> create(@RequestBody CustomerDto customer) {
		SavedCustomerDto saved = customerService.create(customer);
		return ResponseEntity.created(URI.create("/api/customers/" + saved.customer().id())).body(saved);
	}

	@PutMapping("/{id}")
	public SavedCustomerDto update(@PathVariable Long id, @RequestBody CustomerDto customer) {
		return customerService.update(id, customer);
	}

}
