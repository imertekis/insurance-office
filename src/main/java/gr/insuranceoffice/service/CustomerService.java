package gr.insuranceoffice.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.mapper.CustomerMapper;
import gr.insuranceoffice.repository.CustomerRepository;

@Service
public class CustomerService {

	private static final Sort BY_NAME = Sort.by("lastName", "firstName", "id");

	private final CustomerRepository customerRepository;

	private final CustomerMapper customerMapper;

	public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper) {
		this.customerRepository = customerRepository;
		this.customerMapper = customerMapper;
	}

	@Transactional(readOnly = true)
	public List<CustomerDto> findAll() {
		return customerMapper.toDtoList(customerRepository.findAll(BY_NAME));
	}

}
