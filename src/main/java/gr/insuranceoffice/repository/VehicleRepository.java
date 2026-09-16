package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

}
