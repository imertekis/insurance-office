package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OwnershipDto(
		Long id,
		Long vehicleId,
		Long customerId,
		BigDecimal percentage,
		boolean primary,
		LocalDate fromDate,
		LocalDate toDate) {
}
