package gr.insuranceoffice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import gr.insuranceoffice.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

	Optional<Customer> findByTaxId(String taxId);

	// Not unique: family members can share a number.
	List<Customer> findByMobile(String mobile, Sort sort, Limit limit);

	List<Customer> findByPhone(String phone, Sort sort, Limit limit);

}
