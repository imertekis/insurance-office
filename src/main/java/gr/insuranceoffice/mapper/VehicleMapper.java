package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Vehicle;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

	VehicleDto toDto(Vehicle vehicle);

	List<VehicleDto> toDtoList(List<Vehicle> vehicles);

}
