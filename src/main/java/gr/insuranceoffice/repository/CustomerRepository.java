package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

}
