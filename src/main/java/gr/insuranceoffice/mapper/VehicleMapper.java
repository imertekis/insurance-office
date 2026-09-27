package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Vehicle;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

	// The colour as stored, whole: VehicleService splits it for the form.
	@Mapping(target = "secondColor", ignore = true)
	VehicleDto toDto(Vehicle vehicle);

	List<VehicleDto> toDtoList(List<Vehicle> vehicles);

	/**
	 * The same DTO with blank text as null and the rest stripped, so that
	 * validation sees exactly what {@link #updateEntity} will store.
	 */
	VehicleDto withBlanksAsNull(VehicleDto dto);

	/**
	 * Copies the editable fields onto the entity. Id, version, timestamps and
	 * the derived columns have no setters and are left alone:
	 * {@code plate_normalized} is filled by the entity's own callback and
	 * {@code search_normalized} by the database.
	 */
	void updateEntity(VehicleDto dto, @MappingTarget Vehicle vehicle);

	/** An empty form field means "no value", not an empty string. */
	default String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
