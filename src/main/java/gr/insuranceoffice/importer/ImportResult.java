package gr.insuranceoffice.importer;

/**
 * What a successful import did, per table.
 */
public record ImportResult(
		Counts intermediaries,
		Counts customers,
		Counts vehicles,
		Counts ownerships,
		Counts policies) {

	/**
	 * @param created rows that did not exist yet
	 * @param updated rows found by their natural key and overwritten from the file
	 * @param removed ownerships whose owner is no longer in the file, which
	 *                holds only a vehicle's current owners
	 */
	public record Counts(int created, int updated, int removed) {
	}

}
