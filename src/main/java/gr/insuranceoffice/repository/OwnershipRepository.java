package gr.insuranceoffice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {

	List<Ownership> findByVehicle(Vehicle vehicle);

}
