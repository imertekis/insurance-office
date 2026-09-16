package gr.insuranceoffice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Policy;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

	Optional<Policy> findByPolicyNumber(String policyNumber);

}
