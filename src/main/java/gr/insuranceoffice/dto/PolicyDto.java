package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyDto(
		Long id,
		String policyNumber,
		Long vehicleId,
		String insuranceCompany,
		Long intermediaryId,
		LocalDate startDate,
		LocalDate endDate,
		BigDecimal premium,
		boolean surcharge,
		String surchargeType) {
}
