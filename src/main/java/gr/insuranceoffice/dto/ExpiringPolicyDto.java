package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the expiry screen (SPEC §7.1). The customer is the vehicle's
 * current primary owner; customerId and the name are null when it has none.
 */
public record ExpiringPolicyDto(
		Long policyId,
		Long vehicleId,
		String plate,
		Long customerId,
		String lastName,
		String firstName,
		String mobile,
		LocalDate endDate,
		String insuranceCompany,
		BigDecimal premium) {
}
