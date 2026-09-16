package gr.insuranceoffice.dto;

import java.util.List;

/**
 * What the single search box found (SPEC §6), grouped by kind so that every
 * row can carry its type label, ΠΕΛΑΤΗΣ or ΟΧΗΜΑ.
 *
 * @param query      the input as typed
 * @param searchedAs how the input was read; more than one when the pattern is
 *                   ambiguous, e.g. ten digits starting with 21
 * @param customers  matching customers, by name
 * @param vehicles   matching vehicles, by plate; a policy number finds its vehicle
 * @param truncated  a group had more matches than it shows, so the clerk
 *                   should type more
 */
public record SearchResultDto(
		String query,
		List<SearchType> searchedAs,
		List<CustomerHit> customers,
		List<VehicleHit> vehicles,
		boolean truncated) {

	/** The input patterns of SPEC §6. */
	public enum SearchType {
		VIN, PLATE, TAX_ID, MOBILE, PHONE, POLICY_NUMBER, TEXT
	}

	/** «Αλεξίου Κωνσταντίνος — ΑΦΜ 189856820 — 2 οχήματα» */
	public record CustomerHit(
			Long id,
			String lastName,
			String firstName,
			String taxId,
			long vehicleCount) {
	}

	/**
	 * «NZA-8812 — Opel Astra J — Αλεξίου Κωνσταντίνος». The owner fields are
	 * null when the vehicle has no current primary owner.
	 */
	public record VehicleHit(
			Long id,
			String plate,
			String brand,
			String model,
			Long primaryOwnerId,
			String primaryOwnerLastName,
			String primaryOwnerFirstName) {
	}

}
