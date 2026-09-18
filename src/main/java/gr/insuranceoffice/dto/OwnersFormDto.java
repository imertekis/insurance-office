package gr.insuranceoffice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The ownership form of one vehicle: all its current owners together, since
 * the shares only add up as a whole (Task 11c).
 *
 * @param vehicleVersion   the vehicle's version the clerk started from
 * @param transferDate     when owners removed stop and owners added start
 * @param primaryCustomerId the owner marked primary, or null
 */
public record OwnersFormDto(
		Long vehicleId,
		String plate,
		Long vehicleVersion,
		LocalDate transferDate,
		Long primaryCustomerId,
		List<OwnerRowDto> rows) {

	/**
	 * One owner as the form shows it. The share is text, exactly as typed,
	 * so a mistake is shown back rather than lost.
	 */
	public record OwnerRowDto(
			Long customerId,
			String lastName,
			String firstName,
			String taxId,
			String mobile,
			String percentage) {
	}

}
