package gr.insuranceoffice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
