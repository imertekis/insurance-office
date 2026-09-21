package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;

/**
 * The customer of a policy (Task 13): the primary owner when it started, not
 * today's, so that the history shows who held the vehicle then. Shared by the
 * vehicle card and the policy list.
 */
final class PolicyCustomers {

	private PolicyCustomers() {
	}

	/**
	 * The policy with its customer's id and name; unchanged when the vehicle
	 * has no primary owner to name.
	 *
	 * @param ownerships the vehicle's ownerships, at least its primary ones
	 */
	static PolicyViewDto attach(PolicyViewDto policy, List<Ownership> ownerships) {
		Customer owner = primaryOwnerOn(ownerships, policy.startDate());
		return owner == null ? policy : policy.withCustomer(owner.getId(),
				owner.getFirstName() == null ? owner.getLastName() : owner.getLastName() + " " + owner.getFirstName());
	}

	/**
	 * The primary owner on the given day. A transfer date closes the old
	 * ownership and opens the new one (Task 11c), so an ownership covers the
	 * day if it started on or before it and ended after it; an empty date is
	 * open. Imported rows have no dates and so cover every day, which makes
	 * the current primary owner the answer for them. If no ownership covers
	 * the day, for instance the first owner on record started later, the
	 * current primary owner is the fallback.
	 *
	 * @return null if the vehicle has no primary owner to name
	 */
	static Customer primaryOwnerOn(List<Ownership> ownerships, LocalDate day) {
		List<Ownership> primaries = ownerships.stream().filter(Ownership::isPrimary).toList();
		return primaries.stream()
				.filter(ownership -> (ownership.getFromDate() == null || !ownership.getFromDate().isAfter(day))
						&& (ownership.getToDate() == null || day.isBefore(ownership.getToDate())))
				// Should two cover the day, the one that started last holds it.
				.max(Comparator.comparing(Ownership::getFromDate, Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(Ownership::getId))
				.or(() -> primaries.stream().filter(ownership -> ownership.getToDate() == null).findFirst())
				.map(Ownership::getCustomer)
				.orElse(null);
	}

}
