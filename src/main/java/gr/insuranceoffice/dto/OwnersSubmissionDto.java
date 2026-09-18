package gr.insuranceoffice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the ownership form sends. Rows are two parallel lists in form order;
 * the shares are text as typed, and OwnershipService reads them, with the
 * Greek decimal comma accepted.
 */
public record OwnersSubmissionDto(
		Long vehicleVersion,
		LocalDate transferDate,
		Long primaryCustomerId,
		List<Long> customerIds,
		List<String> percentages) {
}
