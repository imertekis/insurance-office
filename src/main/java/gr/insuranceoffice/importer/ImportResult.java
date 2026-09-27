package gr.insuranceoffice.importer;

import java.util.List;

/**
 * What a successful import did, per table, and what it has to say beyond
 * the counts.
 *
 * @param warnings values imported as they came because they are in no list
 *                 (Task 23a, decision 6), in file order, for the clerk to
 *                 correct in the application
 */
public record ImportResult(
		Counts intermediaries,
		Counts customers,
		Counts vehicles,
		Counts ownerships,
		Counts policies,
		List<ImportError> warnings) {

	/**
	 * @param created rows that did not exist yet
	 * @param updated rows found by their natural key and overwritten from the file
	 * @param removed ownerships whose owner is no longer in the file, which
	 *                holds only a vehicle's current owners
	 */
	public record Counts(int created, int updated, int removed) {
	}

}
