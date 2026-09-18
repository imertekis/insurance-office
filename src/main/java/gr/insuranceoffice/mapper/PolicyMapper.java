package gr.insuranceoffice.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import gr.insuranceoffice.dto.PolicyDto;
import gr.insuranceoffice.dto.PolicyFormDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.entity.Policy;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

	// Ids only: the DTO must not drag lazy associations into the view layer.
	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "intermediaryId", source = "intermediary.id")
	PolicyDto toDto(Policy policy);

	List<PolicyDto> toDtoList(List<Policy> policies);

	// The status is worked out by the service, which knows today's date.
	@Mapping(target = "vehicleId", source = "policy.vehicle.id")
	@Mapping(target = "plate", source = "policy.vehicle.plate")
	@Mapping(target = "intermediaryName", source = "policy.intermediary.fullName")
	PolicyViewDto toViewDto(Policy policy, PolicyStatus status);

	// For the edit form: the premium as the clerk would type it, "180,00".
	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "intermediaryId", source = "intermediary.id")
	PolicyFormDto toFormDto(Policy policy);

	/**
	 * The same form with blank text as null and the rest stripped, so that
	 * validation sees exactly what will be stored.
	 */
	PolicyFormDto withBlanksAsNull(PolicyFormDto form);

	/**
	 * Copies the plain fields. The vehicle, the intermediary, the premium and
	 * the surcharge are set by PolicyService: the first two are looked up, the
	 * premium has to be read from what was typed, and an unticked surcharge
	 * arrives as null yet must clear the stored one.
	 */
	@Mapping(target = "vehicle", ignore = true)
	@Mapping(target = "intermediary", ignore = true)
	@Mapping(target = "premium", ignore = true)
	@Mapping(target = "surcharge", ignore = true)
	void updateEntity(PolicyFormDto form, @MappingTarget Policy policy);

	/** An empty form field means "no value", not an empty string. */
	default String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/** 180.00 is written 180,00. */
	default String decimalText(BigDecimal value) {
		return value == null ? null : value.toPlainString().replace('.', ',');
	}

}
