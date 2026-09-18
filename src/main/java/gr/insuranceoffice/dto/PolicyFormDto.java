package gr.insuranceoffice.dto;

import java.time.LocalDate;

/**
 * The policy form. The premium is text, exactly as typed, so "180,50" or a
 * mistake is shown back rather than lost; PolicyService reads it, with the
 * Greek decimal comma accepted (Task 11d).
 */
public record PolicyFormDto(
		Long id,
		Long vehicleId,
		String policyNumber,
		String insuranceCompany,
		Long intermediaryId,
		LocalDate startDate,
		LocalDate endDate,
		String premium,
		// A checkbox sends nothing when unticked, so null means no surcharge.
		Boolean surcharge,
		String surchargeType,
		// Sent back unchanged on save; a mismatch means someone else saved first.
		Long version) {
}
