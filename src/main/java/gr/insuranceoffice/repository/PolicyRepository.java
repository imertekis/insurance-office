package gr.insuranceoffice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import gr.insuranceoffice.entity.Policy;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

	Optional<Policy> findByPolicyNumber(String policyNumber);

	/**
	 * Policies ending between the two days (both included) that have not been
	 * renewed, i.e. no later policy exists for the same vehicle, soonest
	 * first. Each row carries the vehicle's current primary owner, if any, so
	 * the expiry screen (SPEC §7.1) is one query.
	 *
	 * @param insuranceCompany exact name, or null for every company
	 */
	@Query("""
			select p.id as policyId, v.id as vehicleId, v.plate as plate,
				c.id as customerId, c.lastName as lastName, c.firstName as firstName, c.mobile as mobile,
				p.endDate as endDate, p.insuranceCompany as insuranceCompany, p.premium as premium
			from Policy p
			join p.vehicle v
			left join Ownership o on o.vehicle = v and o.primary = true and o.toDate is null
			left join o.customer c
			where p.endDate between :from and :to
			and (:insuranceCompany is null or p.insuranceCompany = :insuranceCompany)
			and not exists (
				select later.id from Policy later
				where later.vehicle = v and later.startDate > p.startDate)
			order by p.endDate, v.plateNormalized, p.id
			""")
	List<ExpiringPolicy> findNotRenewedEndingBetween(LocalDate from, LocalDate to, String insuranceCompany);

	/** Whether the vehicle has a policy in force on the day (start and end included). */
	@Query("""
			select count(p) > 0 from Policy p
			where p.vehicle.id = :vehicleId and p.startDate <= :day and p.endDate >= :day
			""")
	boolean isInsuredOn(Long vehicleId, LocalDate day);

	/**
	 * The vehicle's policies whose period overlaps the given one. Touching is
	 * not overlapping: a policy may start the day the previous one ends, as
	 * the office's renewals do (Task 11d).
	 *
	 * @param excludeId the policy being edited, or null for a new one
	 */
	@Query("""
			select p from Policy p
			where p.vehicle.id = :vehicleId and (:excludeId is null or p.id <> :excludeId)
			and p.startDate < :endDate and p.endDate > :startDate
			order by p.startDate
			""")
	List<Policy> findOverlapping(Long vehicleId, Long excludeId, LocalDate startDate, LocalDate endDate);

	/** A vehicle's policies with their intermediary, newest first. */
	@Query("""
			select p from Policy p left join fetch p.intermediary
			where p.vehicle.id = :vehicleId
			order by p.endDate desc, p.id desc
			""")
	List<Policy> findByVehicleIdWithIntermediary(Long vehicleId);

	/** The policies of every vehicle the customer owns or owned, newest first. */
	@Query("""
			select p from Policy p join fetch p.vehicle v left join fetch p.intermediary
			where v.id in (select o.vehicle.id from Ownership o where o.customer.id = :customerId)
			order by p.endDate desc, p.id desc
			""")
	List<Policy> findByOwnerWithVehicleAndIntermediary(Long customerId);

	/**
	 * A page of the policy list (Task 15) with its vehicle, so the rows show
	 * the plate without a query each. The order comes from the pageable.
	 *
	 * @param insuranceCompany exact name, or null for every company
	 */
	@Query(value = """
			select p from Policy p join fetch p.vehicle
			where (:insuranceCompany is null or p.insuranceCompany = :insuranceCompany)
			""", countQuery = """
			select count(p) from Policy p
			where (:insuranceCompany is null or p.insuranceCompany = :insuranceCompany)
			""")
	Page<Policy> findPage(String insuranceCompany, Pageable pageable);

	@Query("select distinct p.insuranceCompany from Policy p order by p.insuranceCompany")
	List<String> findInsuranceCompanies();

	interface ExpiringPolicy {

		Long getPolicyId();

		Long getVehicleId();

		String getPlate();

		Long getCustomerId();

		String getLastName();

		String getFirstName();

		String getMobile();

		LocalDate getEndDate();

		String getInsuranceCompany();

		BigDecimal getPremium();

	}

}
