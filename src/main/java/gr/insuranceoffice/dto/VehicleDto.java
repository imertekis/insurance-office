package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VehicleDto(
		Long id,
		String vin,
		String plate,
		String brand,
		String model,
		LocalDate firstRegistration,
		LocalDate licenseIssueDate,
		String category,
		String usageType,
		String color,
		Short seats,
		Integer engineCc,
		BigDecimal powerKw,
		String fuelType,
		String engineNumber,
		Integer co2,
		String emissionStandard,
		Integer weightKg,
		String licenseStreet,
		String licenseCity,
		String licensePostalCode,
		// Sent back unchanged on save; a mismatch means someone else saved first.
		Long version) {
}
