package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gr.insuranceoffice.dto.PolicyDto;
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

}
