package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.VehicleDetailDto;
import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.mapper.VehicleMapper;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.util.TextNormalizationUtils;

/** The vehicle card (SPEC §7.2) and the rules its form must satisfy. */
@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;

	private final OwnershipRepository ownershipRepository;

	private final PolicyRepository policyRepository;

	private final VehicleMapper vehicleMapper;

	private final OwnershipMapper ownershipMapper;

	private final PolicyMapper policyMapper;

	// SPEC §8: I, O and Q never appear in a VIN.
	private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");

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

	/**
	 * The vehicle's own fields, for the edit form.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@Transactional(readOnly = true)
	public VehicleDto find(Long id) {
		return vehicleRepository.findById(id)
				.map(vehicleMapper::toDto)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε."));
	}

	/**
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public VehicleDto create(VehicleDto dto) {
		VehicleDto values = cleaned(dto);
		checkFields(values, null).throwIfAny();

		Vehicle vehicle = new Vehicle();
		vehicleMapper.updateEntity(values, vehicle);
		return vehicleMapper.toDto(vehicleRepository.saveAndFlush(vehicle));
	}

	/**
	 * @param dto carries the version the clerk started editing from
	 * @throws NotFoundException if the vehicle no longer exists
	 * @throws ObjectOptimisticLockingFailureException if someone else saved it
	 *             in the meantime
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public VehicleDto update(Long id, VehicleDto dto) {
		Vehicle vehicle = vehicleRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν υπάρχει πια."));
		if (!Objects.equals(dto.version(), vehicle.getVersion())) {
			throw new ObjectOptimisticLockingFailureException(Vehicle.class, id);
		}

		VehicleDto values = cleaned(dto);
		checkFields(values, id).throwIfAny();

		vehicleMapper.updateEntity(values, vehicle);
		// Flushed here so that a concurrent change fails inside this call and
		// the returned version is the new one.
		vehicleRepository.flush();
		return vehicleMapper.toDto(vehicle);
	}

	// A VIN is written in capitals, whatever the clerk typed. The plate keeps
	// the form it was typed in (DATA_MODEL); only plate_normalized is folded.
	private VehicleDto cleaned(VehicleDto dto) {
		VehicleDto values = vehicleMapper.withBlanksAsNull(dto);
		return values.vin() == null ? values : new VehicleDto(values.id(), values.vin().toUpperCase(Locale.ROOT),
				values.plate(), values.brand(), values.model(), values.firstRegistration(),
				values.licenseIssueDate(), values.category(), values.usageType(), values.color(), values.seats(),
				values.engineCc(), values.powerKw(), values.fuelType(), values.engineNumber(), values.co2(),
				values.emissionStandard(), values.weightKg(), values.licenseStreet(), values.licenseCity(),
				values.licensePostalCode(), values.version());
	}

	private Violations checkFields(VehicleDto values, Long id) {
		Violations violations = new Violations();
		violations.required("vin", values.vin(), "Ο αριθμός πλαισίου (VIN) είναι υποχρεωτικός.");
		violations.format("vin", values.vin(), VIN, "Το VIN έχει 17 χαρακτήρες, χωρίς τα γράμματα I, O και Q.");
		if (values.vin() != null && VIN.matcher(values.vin()).matches()) {
			violations.addIf(vehicleRepository.findByVin(values.vin())
					.filter(other -> !other.getId().equals(id)).isPresent(),
					"vin", "Υπάρχει ήδη όχημα με αυτό το VIN.");
		}

		violations.required("plate", values.plate(), "Ο αριθμός κυκλοφορίας είναι υποχρεωτικός.");
		if (values.plate() != null) {
			// ΑΒΕ-1234 and ABE-1234 are the same plate (CLAUDE.md §5).
			violations.addIf(vehicleRepository.findByPlateNormalized(TextNormalizationUtils
					.normalizePlate(values.plate())).filter(other -> !other.getId().equals(id)).isPresent(),
					"plate", "Υπάρχει ήδη όχημα με αυτή την πινακίδα· "
							+ "τα ελληνικά και τα λατινικά γράμματα μετρούν ως ίδια.");
		}

		violations.required("brand", values.brand(), "Η μάρκα είναι υποχρεωτική.");
		violations.required("model", values.model(), "Το μοντέλο είναι υποχρεωτικό.");
		violations.required("firstRegistration", values.firstRegistration(),
				"Η ημερομηνία 1ης άδειας είναι υποχρεωτική.");
		violations.required("category", values.category(), "Η κατηγορία είναι υποχρεωτική.");
		violations.required("color", values.color(), "Το χρώμα είναι υποχρεωτικό.");
		violations.addIf(!isValid(values.usageType(), Vehicle.UsageType.class),
				"usageType", "Επιλέξτε χρήση οχήματος.");
		violations.addIf(!isValid(values.fuelType(), Vehicle.FuelType.class),
				"fuelType", "Επιλέξτε καύσιμο.");

		violations.required("powerKw", values.powerKw(), "Η ισχύς σε kW είναι υποχρεωτική.");
		violations.addIf(values.powerKw() != null && values.powerKw().signum() <= 0,
				"powerKw", "Η ισχύς πρέπει να είναι θετικός αριθμός.");

		// DATA_MODEL: an electric vehicle has no engine capacity, and a 0 would
		// skew averages, so the field stays empty for it and is required
		// otherwise.
		if (Vehicle.FuelType.ΗΛΕΚΤΡΙΣΜΟΣ.name().equals(values.fuelType())) {
			violations.addIf(values.engineCc() != null, "engineCc",
					"Ηλεκτρικό όχημα δεν έχει κυβικά· αφήστε το πεδίο κενό.");
		} else {
			violations.required("engineCc", values.engineCc(), "Τα κυβικά είναι υποχρεωτικά.");
			violations.addIf(values.engineCc() != null && values.engineCc() <= 0,
					"engineCc", "Τα κυβικά πρέπει να είναι θετικός αριθμός.");
		}

		violations.addIf(values.seats() != null && values.seats() <= 0,
				"seats", "Οι θέσεις πρέπει να είναι θετικός αριθμός.");
		violations.addIf(values.co2() != null && values.co2() < 0, "co2", "Το CO2 δεν μπορεί να είναι αρνητικό.");
		violations.addIf(values.weightKg() != null && values.weightKg() <= 0,
				"weightKg", "Το βάρος πρέπει να είναι θετικός αριθμός.");
		violations.format("licensePostalCode", values.licensePostalCode(), POSTAL_CODE,
				"Ο Τ.Κ. πρέπει να έχει 5 ψηφία.");
		return violations;
	}

	private static <E extends Enum<E>> boolean isValid(String value, Class<E> type) {
		return value != null && Arrays.stream(type.getEnumConstants()).anyMatch(constant -> constant.name()
				.equals(value));
	}

	private List<PolicyViewDto> policyViews(List<Policy> policies) {
		LocalDate today = LocalDate.now();
		return policies.stream()
				.map(policy -> policyMapper.toViewDto(policy,
						PolicyStatus.of(policy.getStartDate(), policy.getEndDate(), today)))
				.toList();
	}

}
