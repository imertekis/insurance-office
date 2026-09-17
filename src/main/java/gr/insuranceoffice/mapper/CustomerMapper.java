package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.entity.Customer;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

	CustomerDto toDto(Customer customer);

	List<CustomerDto> toDtoList(List<Customer> customers);

	/**
	 * The same DTO with blank text as null and the rest stripped, so that
	 * validation sees exactly what {@link #updateEntity} will store.
	 */
	CustomerDto withBlanksAsNull(CustomerDto dto);

	/**
	 * Copies the editable fields onto the entity. Id, version, timestamps and
	 * the generated search column have no setters and are left alone.
	 */
	void updateEntity(CustomerDto dto, @MappingTarget Customer customer);

	/**
	 * Applied by MapStruct to every text field. An empty form field means
	 * "no value": stored as "", a missing ΑΦΜ would collide with the next
	 * one on the unique index.
	 */
	default String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
