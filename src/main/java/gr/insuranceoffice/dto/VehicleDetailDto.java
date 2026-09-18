package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The vehicle card (SPEC §7.2): the licence details, the owners with their
 * shares and the policies, current ones first.
 */
public record VehicleDetailDto(
		VehicleDto vehicle,
		List<OwnerDto> owners,
		List<PolicyViewDto> policies) {

	/** Current owners come first; a former owner keeps its transfer dates. */
	public record OwnerDto(
			Long ownershipId,
			Long customerId,
			String lastName,
			String firstName,
			String taxId,
			String mobile,
			BigDecimal percentage,
			boolean primary,
			LocalDate fromDate,
			LocalDate toDate,
			boolean current) {
	}

}
