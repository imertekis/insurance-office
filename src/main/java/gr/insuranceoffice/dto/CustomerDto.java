package gr.insuranceoffice.dto;

import java.time.LocalDate;

public record CustomerDto(
		Long id,
		String taxId,
		String entityType,
		String lastName,
		String firstName,
		String fatherName,
		LocalDate birthDate,
		LocalDate licenseDate,
		String taxOffice,
		String street,
		String city,
		String postalCode,
		String mobile,
		String phone,
		String email,
		String notes) {
}
