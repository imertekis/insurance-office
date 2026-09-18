package gr.insuranceoffice.dto;

import java.util.List;

/**
 * What the delete confirmation page shows before a hard delete (Task 11e).
 *
 * @param subject     the record, e.g. «ο πελάτης Αλεξίου Μαρία»
 * @param alsoDeleted what goes with it, e.g. a vehicle's policies
 * @param blockers    why it cannot be deleted now; empty when it can
 * @param vehicleId   the vehicle card the record belongs to, to return to;
 *                    null for a customer
 */
public record DeletionPreviewDto(
		String subject,
		List<String> alsoDeleted,
		List<String> blockers,
		Long vehicleId) {

	public boolean isBlocked() {
		return !blockers.isEmpty();
	}

}
