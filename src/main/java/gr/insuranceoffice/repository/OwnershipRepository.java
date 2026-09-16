package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Ownership;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {

}
