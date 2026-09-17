package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.VehicleDetailDto;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.mapper.VehicleMapper;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/** Reads for the vehicle card (SPEC §7.2). */
@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;

	private final OwnershipRepository ownershipRepository;

	private final PolicyRepository policyRepository;

	private final VehicleMapper vehicleMapper;

	private final OwnershipMapper ownershipMapper;

	private final PolicyMapper policyMapper;

	public VehicleService(VehicleRepository vehicleRepository, OwnershipRepository ownershipRepository,
			PolicyRepository policyRepository, VehicleMapper vehicleMapper, OwnershipMapper ownershipMapper,
			PolicyMapper policyMapper) {
		this.vehicleRepository = vehicleRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.vehicleMapper = vehicleMapper;
		this.ownershipMapper = ownershipMapper;
		this.policyMapper = policyMapper;
	}

	/**
	 * The vehicle with its owners and its policies: three queries, whatever
	 * the number of rows.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@Transactional(readOnly = true)
	public VehicleDetailDto findDetail(Long id) {
		return vehicleRepository.findById(id)
				.map(vehicle -> new VehicleDetailDto(vehicleMapper.toDto(vehicle),
						ownershipMapper.toOwnerDtoList(ownershipRepository.findByVehicleIdWithCustomer(id)),
						policyViews(policyRepository.findByVehicleIdWithIntermediary(id))))
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε."));
	}

	private List<PolicyViewDto> policyViews(List<Policy> policies) {
		LocalDate today = LocalDate.now();
		return policies.stream()
				.map(policy -> policyMapper.toViewDto(policy, PolicyStatus.of(policy.getEndDate(), today)))
				.toList();
	}

}
