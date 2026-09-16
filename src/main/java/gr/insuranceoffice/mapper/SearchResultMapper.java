package gr.insuranceoffice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Vehicle;

// The counts and owners come in as arguments, loaded for all hits at once,
// so that mapping never touches a lazy association.
@Mapper(componentModel = "spring")
public interface SearchResultMapper {

	CustomerHit toCustomerHit(Customer customer, long vehicleCount);

	@Mapping(target = "id", source = "vehicle.id")
	@Mapping(target = "primaryOwnerId", source = "primaryOwner.id")
	@Mapping(target = "primaryOwnerLastName", source = "primaryOwner.lastName")
	@Mapping(target = "primaryOwnerFirstName", source = "primaryOwner.firstName")
	VehicleHit toVehicleHit(Vehicle vehicle, Customer primaryOwner);

}
