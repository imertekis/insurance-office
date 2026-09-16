package gr.insuranceoffice.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.entity.Customer;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

	CustomerDto toDto(Customer customer);

	List<CustomerDto> toDtoList(List<Customer> customers);

}
