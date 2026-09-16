package gr.insuranceoffice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import gr.insuranceoffice.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

	Optional<Vehicle> findByVin(String vin);

	Optional<Vehicle> findByPlateNormalized(String plateNormalized);

	@Query("select p.vehicle from Policy p where p.policyNumber = :policyNumber")
	Optional<Vehicle> findByPolicyNumber(String policyNumber);

}
