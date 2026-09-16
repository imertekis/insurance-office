package gr.insuranceoffice.dto;

public record IntermediaryDto(
		Long id,
		String fullName,
		String registryNumber,
		String phone,
		String email,
		boolean active) {
}
