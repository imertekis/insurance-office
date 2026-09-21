package gr.insuranceoffice.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.mapper.SearchResultMapper;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.OwnershipRepository.VehicleCount;

/**
 * The rows of the search results and of the customer and vehicle lists
 * (Task 15), which are the same: each customer with the number of vehicles
 * they own, each vehicle with its primary owner. The counts and owners are
 * loaded for all rows at once, however many there are.
 */
@Component
class HitAssembler {

	private final OwnershipRepository ownershipRepository;

	private final SearchResultMapper searchResultMapper;

	HitAssembler(OwnershipRepository ownershipRepository, SearchResultMapper searchResultMapper) {
		this.ownershipRepository = ownershipRepository;
		this.searchResultMapper = searchResultMapper;
	}

	List<CustomerHit> customerHits(List<Customer> customers) {
		if (customers.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> vehicleCounts = ownershipRepository
				.countCurrentVehicles(customers.stream().map(Customer::getId).toList()).stream()
				.collect(Collectors.toMap(VehicleCount::getCustomerId, VehicleCount::getVehicleCount));
		return customers.stream()
				.map(customer -> searchResultMapper.toCustomerHit(customer,
						vehicleCounts.getOrDefault(customer.getId(), 0L)))
				.toList();
	}

	List<VehicleHit> vehicleHits(List<Vehicle> vehicles) {
		if (vehicles.isEmpty()) {
			return List.of();
		}
		Map<Long, Customer> primaryOwners = ownershipRepository
				.findCurrentPrimaryOwners(vehicles.stream().map(Vehicle::getId).toList()).stream()
				// The ownership rule allows one primary owner; a search never
				// fails over data that breaks it.
				.collect(Collectors.toMap(ownership -> ownership.getVehicle().getId(), Ownership::getCustomer,
						(first, second) -> first));
		return vehicles.stream()
				.map(vehicle -> searchResultMapper.toVehicleHit(vehicle, primaryOwners.get(vehicle.getId())))
				.toList();
	}

}
