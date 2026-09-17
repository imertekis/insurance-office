package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy as the vehicle and customer cards show it: with its vehicle's
 * plate, the intermediary's name and how it stands today.
 */
public record PolicyViewDto(
		Long id,
		String policyNumber,
		Long vehicleId,
		String plate,
		String insuranceCompany,
		String intermediaryName,
		LocalDate startDate,
		LocalDate endDate,
		BigDecimal premium,
		boolean surcharge,
		String surchargeType,
		PolicyStatus status) {
}
