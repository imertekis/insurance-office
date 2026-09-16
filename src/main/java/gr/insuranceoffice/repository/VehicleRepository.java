package gr.insuranceoffice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	Optional<Vehicle> findByVin(String vin);

}
