package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.VehicleBrand;

public interface VehicleBrandRepository extends JpaRepository<VehicleBrand, Long> {
}
