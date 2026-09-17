package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import gr.insuranceoffice.dto.ExpiringPolicyDto;
import gr.insuranceoffice.repository.PolicyRepository.ExpiringPolicy;

@Mapper(componentModel = "spring")
public interface DashboardMapper {

	ExpiringPolicyDto toDto(ExpiringPolicy row);

	List<ExpiringPolicyDto> toDtoList(List<ExpiringPolicy> rows);

}
