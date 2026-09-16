package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gr.insuranceoffice.dto.OwnershipDto;
import gr.insuranceoffice.entity.Ownership;

@Mapper(componentModel = "spring")
public interface OwnershipMapper {

	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "customerId", source = "customer.id")
	OwnershipDto toDto(Ownership ownership);

	List<OwnershipDto> toDtoList(List<Ownership> ownerships);

}
