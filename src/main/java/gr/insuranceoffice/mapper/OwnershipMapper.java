package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gr.insuranceoffice.dto.CustomerDetailDto.OwnedVehicleDto;
import gr.insuranceoffice.dto.OwnershipDto;
import gr.insuranceoffice.dto.VehicleDetailDto.OwnerDto;
import gr.insuranceoffice.entity.Ownership;

@Mapper(componentModel = "spring")
public interface OwnershipMapper {

	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "customerId", source = "customer.id")
	OwnershipDto toDto(Ownership ownership);

	List<OwnershipDto> toDtoList(List<Ownership> ownerships);

	// For the vehicle card: the owner behind the share.
	@Mapping(target = "customerId", source = "customer.id")
	@Mapping(target = "lastName", source = "customer.lastName")
	@Mapping(target = "firstName", source = "customer.firstName")
	@Mapping(target = "taxId", source = "customer.taxId")
	@Mapping(target = "mobile", source = "customer.mobile")
	@Mapping(target = "current", expression = "java(ownership.getToDate() == null)")
	OwnerDto toOwnerDto(Ownership ownership);

	List<OwnerDto> toOwnerDtoList(List<Ownership> ownerships);

	// For the customer card: the vehicle behind the share.
	@Mapping(target = "vehicleId", source = "vehicle.id")
	@Mapping(target = "plate", source = "vehicle.plate")
	@Mapping(target = "brand", source = "vehicle.brand")
	@Mapping(target = "model", source = "vehicle.model")
	@Mapping(target = "current", expression = "java(ownership.getToDate() == null)")
	OwnedVehicleDto toOwnedVehicleDto(Ownership ownership);

	List<OwnedVehicleDto> toOwnedVehicleDtoList(List<Ownership> ownerships);

}
