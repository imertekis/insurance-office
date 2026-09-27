package gr.insuranceoffice.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import gr.insuranceoffice.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

	Optional<Vehicle> findByVin(String vin);

	Optional<Vehicle> findByPlateNormalized(String plateNormalized);

	@Query("select p.vehicle from Policy p where p.policyNumber = :policyNumber")
	Optional<Vehicle> findByPolicyNumber(String policyNumber);

	/**
	 * The vehicle, with its version raised when the transaction commits.
	 * Ownership has no version of its own, so saving a vehicle's owners counts
	 * as a change of the vehicle: two clerks editing the owners at once cannot
	 * both win (SPEC §9).
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select v from Vehicle v where v.id = :id")
	Optional<Vehicle> findForOwnersChange(Long id);

	/**
	 * Every brand and model stored, each pair once, for the vehicle form's
	 * suggestions (Task 23b): two columns, not the vehicles.
	 */
	@Query("select distinct v.brand as brand, v.model as model from Vehicle v order by v.brand, v.model")
	List<BrandModel> findBrandModels();

	interface BrandModel {

		String getBrand();

		String getModel();

	}

}
