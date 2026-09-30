package gr.insuranceoffice.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {

	List<Ownership> findByVehicle(Vehicle vehicle);

	/**
	 * A vehicle's owners with their customer: current owners first, then
	 * primary and largest share first.
	 */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id = :vehicleId
			order by o.toDate nulls first, o.primary desc, o.percentage desc, o.id
			""")
	List<Ownership> findByVehicleIdWithCustomer(Long vehicleId);

	/** A vehicle's current owners with their customer, primary first. */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id = :vehicleId and o.toDate is null
			order by o.primary desc, o.percentage desc, o.id
			""")
	List<Ownership> findCurrentByVehicleIdWithCustomer(Long vehicleId);

	/**
	 * The customers' ownerships of a vehicle that start on the given day,
	 * with their customer, in one query for all of them. A new row for one
	 * of these customers starting that day would break the unique index
	 * (vehicle, customer, start) (Task 18).
	 */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id = :vehicleId and o.customer.id in :customerIds and o.fromDate = :fromDate
			order by o.id
			""")
	List<Ownership> findStartingOn(Long vehicleId, Collection<Long> customerIds, LocalDate fromDate);

	/**
	 * The customers' ownerships of a vehicle that end on the given day, with
	 * their customer, in one query for all of them: latest start first, an
	 * empty start (the import's) last, then the newest row. A customer who
	 * joins on the day they were removed gets that row back (Task 19).
	 */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id = :vehicleId and o.customer.id in :customerIds and o.toDate = :toDate
			order by o.fromDate desc nulls last, o.id desc
			""")
	List<Ownership> findEndingOn(Long vehicleId, Collection<Long> customerIds, LocalDate toDate);

	/** A customer's vehicles with their share, current ownerships first. */
	@Query("""
			select o from Ownership o join fetch o.vehicle
			where o.customer.id = :customerId
			order by o.toDate nulls first, o.vehicle.plateNormalized, o.id
			""")
	List<Ownership> findByCustomerIdWithVehicle(Long customerId);

	/** The current primary ownership of each vehicle, with its customer, in one query for all of them. */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id in :vehicleIds and o.primary = true and o.toDate is null
			""")
	List<Ownership> findCurrentPrimaryOwners(Collection<Long> vehicleIds);

	/**
	 * The primary ownerships of the vehicles, current and former, with their
	 * customer, in one query for all of them: what it takes to say who held
	 * each vehicle at any date.
	 */
	@Query("""
			select o from Ownership o join fetch o.customer
			where o.vehicle.id in :vehicleIds and o.primary = true
			""")
	List<Ownership> findPrimaryByVehicleIdsWithCustomer(Collection<Long> vehicleIds);

	/** How many vehicles each customer currently owns, in one query for all of them. */
	@Query("""
			select o.customer.id as customerId, count(distinct o.vehicle.id) as vehicleCount
			from Ownership o
			where o.customer.id in :customerIds and o.toDate is null
			group by o.customer.id
			""")
	List<VehicleCount> countCurrentVehicles(Collection<Long> customerIds);

	/**
	 * Whether the customer is the current primary owner of a vehicle with a
	 * policy in force on the given day (start and end dates included).
	 */
	@Query("""
			select count(o) > 0 from Ownership o
			where o.customer.id = :customerId and o.primary = true and o.toDate is null
			and exists (
				select p.id from Policy p
				where p.vehicle = o.vehicle and p.startDate <= :day and p.endDate >= :day)
			""")
	boolean isCurrentPrimaryOwnerOfInsuredVehicle(Long customerId, LocalDate day);

	interface VehicleCount {

		Long getCustomerId();

		long getVehicleCount();

	}

}
