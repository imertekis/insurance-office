package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Intermediary;

public interface IntermediaryRepository extends JpaRepository<Intermediary, Long> {

}
