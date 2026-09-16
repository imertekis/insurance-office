package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gr.insuranceoffice.dto.PolicyDto;
import gr.insuranceoffice.entity.Policy;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

	// Ids only: the DTO must not drag lazy associations into the view layer.
	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "intermediaryId", source = "intermediary.id")
	PolicyDto toDto(Policy policy);

	List<PolicyDto> toDtoList(List<Policy> policies);

}
