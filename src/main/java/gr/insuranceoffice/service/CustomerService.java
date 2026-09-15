package gr.insuranceoffice.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.repository.CustomerRepository;

@Service
public class CustomerService {

	private static final Sort BY_NAME = Sort.by("lastName", "firstName", "id");

	private final CustomerRepository customerRepository;

	public CustomerService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@Transactional(readOnly = true)
	public List<CustomerDto> findAll() {
		return customerRepository.findAll(BY_NAME).stream()
				.map(CustomerService::toDto)
				.toList();
	}

	// Hand-written until MapStruct mappers are introduced in Task 3.
	private static CustomerDto toDto(Customer customer) {
		return new CustomerDto(
				customer.getId(),
				customer.getTaxId(),
				customer.getEntityType().name(),
				customer.getLastName(),
				customer.getFirstName(),
				customer.getFatherName(),
				customer.getBirthDate(),
				customer.getLicenseDate(),
				customer.getTaxOffice(),
				customer.getStreet(),
				customer.getCity(),
				customer.getPostalCode(),
				customer.getMobile(),
				customer.getPhone(),
				customer.getEmail(),
				customer.getNotes());
	}

}
