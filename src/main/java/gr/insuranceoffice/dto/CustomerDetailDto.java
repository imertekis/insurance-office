package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The customer card (SPEC §7.3): their details, the vehicles they own with
 * their share, and the policies of those vehicles together, the ones that
 * started while the customer owned the vehicle.
 */
public record CustomerDetailDto(
		CustomerDto customer,
		List<OwnedVehicleDto> vehicles,
		List<PolicyViewDto> policies) {

	public record OwnedVehicleDto(
			Long vehicleId,
			String plate,
			String brand,
			String model,
			BigDecimal percentage,
			boolean primary,
			LocalDate fromDate,
			LocalDate toDate,
			boolean current) {
	}

}
