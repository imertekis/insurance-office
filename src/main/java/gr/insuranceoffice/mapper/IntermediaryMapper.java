package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import gr.insuranceoffice.dto.IntermediaryDto;
import gr.insuranceoffice.entity.Intermediary;

@Mapper(componentModel = "spring")
public interface IntermediaryMapper {

	IntermediaryDto toDto(Intermediary intermediary);

	List<IntermediaryDto> toDtoList(List<Intermediary> intermediaries);

}
