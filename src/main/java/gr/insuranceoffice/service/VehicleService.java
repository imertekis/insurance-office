package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.DeletionPreviewDto;
import gr.insuranceoffice.dto.PageDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.dto.VehicleDetailDto;
import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.mapper.VehicleMapper;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.security.Roles;
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

	private final HitAssembler hitAssembler;

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// SPEC §8: I, O and Q never appear in a VIN.
	private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");

	public VehicleService(VehicleRepository vehicleRepository, OwnershipRepository ownershipRepository,
			PolicyRepository policyRepository, VehicleMapper vehicleMapper, OwnershipMapper ownershipMapper,
			PolicyMapper policyMapper, HitAssembler hitAssembler) {
		this.vehicleRepository = vehicleRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.vehicleMapper = vehicleMapper;
		this.ownershipMapper = ownershipMapper;
		this.policyMapper = policyMapper;
		this.hitAssembler = hitAssembler;
	}

	/**
	 * A page of the vehicle list (Task 15), by plate. The order is that of
	 * {@code plate_normalized}, the form plates are compared in everywhere, so
	 * a plate typed with Greek letters and one typed with Latin ones stay
	 * together; it is unique, which keeps the pages from overlapping.
	 *
	 * @param page from 1; a page past the end gives the last one
	 */
	@Transactional(readOnly = true)
	public PageDto<VehicleHit> list(SortDirection direction, int page) {
		return Paging.page(page, direction, List.of("plateNormalized"),
				pageable -> vehicleRepository.findAll(pageable), hitAssembler::vehicleHits);
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
				.map(vehicle -> {
					List<Policy> policies = policyRepository.findByVehicleIdWithIntermediary(id);
					List<Ownership> ownerships = ownershipRepository.findByVehicleIdWithCustomer(id);
					return new VehicleDetailDto(vehicleMapper.toDto(vehicle),
							ownershipMapper.toOwnerDtoList(ownerships),
							policyViews(policies, ownerships), latest(policies));
				})
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

	/**
	 * What deleting the vehicle takes with it (Task 11e): all its policies
	 * and ownerships, current and former.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional(readOnly = true)
	public DeletionPreviewDto deletionPreview(Long id) {
		Vehicle vehicle = vehicleRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε."));
		List<String> alsoDeleted = new ArrayList<>();
		for (Policy policy : policyRepository.findByVehicleIdWithIntermediary(id)) {
			alsoDeleted.add("Το συμβόλαιο " + policy.getPolicyNumber() + " ("
					+ policy.getStartDate().format(GREEK_DATE) + " – " + policy.getEndDate().format(GREEK_DATE) + ")");
		}
		for (Ownership ownership : ownershipRepository.findByVehicleIdWithCustomer(id)) {
			Customer owner = ownership.getCustomer();
			alsoDeleted.add((ownership.getToDate() == null ? "Η ιδιοκτησία του " : "Η παλιά ιδιοκτησία του ")
					+ (owner.getFirstName() == null ? owner.getLastName()
							: owner.getLastName() + " " + owner.getFirstName())
					+ " (" + ownership.getPercentage().stripTrailingZeros().toPlainString().replace('.', ',') + "%)");
		}
		return new DeletionPreviewDto("Το όχημα " + vehicle.getPlate() + " (" + vehicle.getBrand() + " "
				+ vehicle.getModel() + ")", alsoDeleted, List.of(), null);
	}

	/**
	 * Hard delete (DECISIONS §4): the vehicle, its policies and its
	 * ownerships go, each written to audit_log, from where they can be put
	 * back.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional
	public void delete(Long id) {
		// Loaded, so the children are removed through JPA and logged (Task 6).
		vehicleRepository.delete(vehicleRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε.")));
	}

	// A VIN is written in capitals, whatever the clerk typed. The plate loses
	// its dashes and spaces and keeps its letters as typed (Task 14); only
	// plate_normalized is folded. Done before validation, which so sees
	// exactly what will be stored, and "-" alone counts as empty.
	private VehicleDto cleaned(VehicleDto dto) {
		VehicleDto values = vehicleMapper.withBlanksAsNull(dto);
		String plate = vehicleMapper.blankToNull(TextNormalizationUtils.stripPlateSeparators(values.plate()));
		return new VehicleDto(values.id(), values.vin() == null ? null : values.vin().toUpperCase(Locale.ROOT),
				plate, values.brand(), values.model(), values.firstRegistration(),
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
			// ΑΒΕ1234 and ABE1234 are the same plate (CLAUDE.md §5).
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

	// The policy no later one follows: the dashboard's definition of "not
	// renewed" (SPEC §7.1), which Task 12 renews.
	private static Long latest(List<Policy> policies) {
		return policies.stream()
				.max(Comparator.comparing(Policy::getStartDate).thenComparing(Policy::getId))
				.map(Policy::getId)
				.orElse(null);
	}

	// Each policy carries the customer who held the vehicle when it started
	// (Task 13), so the history shows who it was then, not who owns it today.
	private List<PolicyViewDto> policyViews(List<Policy> policies, List<Ownership> ownerships) {
		LocalDate today = LocalDate.now();
		return policies.stream()
				.map(policy -> PolicyCustomers.attach(policyMapper.toViewDto(policy,
						PolicyStatus.of(policy.getStartDate(), policy.getEndDate(), today)), ownerships))
				.toList();
	}

}
