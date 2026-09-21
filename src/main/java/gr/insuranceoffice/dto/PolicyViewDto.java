package gr.insuranceoffice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy as the vehicle and customer cards show it: with its vehicle's
 * plate and how it stands today.
 *
 * @param customerId   the customer the policy belonged to when it started
 *                     (Task 13); set on the vehicle card only, null elsewhere
 * @param customerName that customer's name, null with {@code customerId}
 */
public record PolicyViewDto(
		Long id,
		String policyNumber,
		Long vehicleId,
		String plate,
		String insuranceCompany,
		Long customerId,
		String customerName,
		LocalDate startDate,
		LocalDate endDate,
		BigDecimal premium,
		boolean surcharge,
		String surchargeType,
		PolicyStatus status) {

	public PolicyViewDto withCustomer(Long customerId, String customerName) {
		return new PolicyViewDto(id, policyNumber, vehicleId, plate, insuranceCompany, customerId, customerName,
				startDate, endDate, premium, surcharge, surchargeType, status);
	}

}
