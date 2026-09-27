package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import gr.insuranceoffice.entity.VehicleBrand;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.mapper.VehicleMapper;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleBrandRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.security.Roles;
import gr.insuranceoffice.util.TextNormalizationUtils;

/** The vehicle card (SPEC §7.2) and the rules its form must satisfy. */
@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;

	private final OwnershipRepository ownershipRepository;

	private final PolicyRepository policyRepository;

	private final VehicleBrandRepository vehicleBrandRepository;

	private final VehicleMapper vehicleMapper;

	private final OwnershipMapper ownershipMapper;

	private final PolicyMapper policyMapper;

	private final HitAssembler hitAssembler;

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// SPEC §8: I, O and Q never appear in a VIN.
	private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");

	public VehicleService(VehicleRepository vehicleRepository, OwnershipRepository ownershipRepository,
			PolicyRepository policyRepository, VehicleBrandRepository vehicleBrandRepository,
			VehicleMapper vehicleMapper, OwnershipMapper ownershipMapper, PolicyMapper policyMapper,
			HitAssembler hitAssembler) {
		this.vehicleRepository = vehicleRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.vehicleBrandRepository = vehicleBrandRepository;
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
		checkFields(values, vehicle).throwIfAny();

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

	/**
	 * The VIN rule, shared with the Excel import (Task 17). The VIN is given
	 * as stored, in capitals.
	 */
	public static boolean isVin(String vin) {
		return vin != null && VIN.matcher(vin).matches();
	}

	/**
	 * The seats rule, shared with the Excel import (Task 23a, decision 8): 1
	 * to 99 whatever the category, which catches 0, negatives and three digits
	 * without a table of limits per category.
	 */
	public static boolean isSeatCount(short seats) {
		return seats >= 1 && seats <= 99;
	}

	/**
	 * The brands, for the form's rule and the Excel import's mapping (Task
	 * 23a). Read on every call: some hundred rows, and a new brand comes with
	 * a migration.
	 */
	@Transactional(readOnly = true)
	public VehicleValues.Brands brands() {
		return new VehicleValues.Brands(vehicleBrandRepository.findAll().stream()
				.collect(Collectors.toMap(VehicleBrand::getName, VehicleBrand::getSynonyms)));
	}

	// The values as Vehicle will store them (Tasks 14, 17): the VIN in
	// capitals, the plate without dashes or spaces, in capitals and without
	// accents, its alphabet as typed. Done before validation, which so sees
	// exactly what will be stored, and "-" alone counts as empty.
	private VehicleDto cleaned(VehicleDto dto) {
		VehicleDto values = vehicleMapper.withBlanksAsNull(dto);
		String plate = vehicleMapper.blankToNull(TextNormalizationUtils.storedPlate(values.plate()));
		return new VehicleDto(values.id(), TextNormalizationUtils.storedVin(values.vin()),
				plate, values.brand(), values.model(), values.firstRegistration(),
				values.licenseIssueDate(), values.category(), values.usageType(), values.color(), values.seats(),
				values.engineCc(), values.powerKw(), values.fuelType(), values.engineNumber(), values.co2(),
				values.emissionStandard(), values.weightKg(), values.licenseStreet(), values.licenseCity(),
				values.licensePostalCode(), values.version());
	}

	/**
	 * @param stored the vehicle as saved before this edit, or null for a new
	 *               one: its old values outside the lists stay allowed
	 */
	private Violations checkFields(VehicleDto values, Vehicle stored) {
		Long id = stored == null ? null : stored.getId();
		Violations violations = new Violations();
		violations.required("vin", values.vin(), "Ο αριθμός πλαισίου (VIN) είναι υποχρεωτικός.");
		violations.format("vin", values.vin(), VIN, "Το VIN έχει 17 χαρακτήρες, χωρίς τα γράμματα I, O και Q.");
		if (isVin(values.vin())) {
			violations.addIf(vehicleRepository.findByVin(values.vin())
					.filter(other -> !other.getId().equals(id)).isPresent(), UniqueConstraint.VEHICLE_VIN);
		}

		violations.required("plate", values.plate(), "Ο αριθμός κυκλοφορίας είναι υποχρεωτικός.");
		if (values.plate() != null) {
			// ΑΒΕ1234 and ABE1234 are the same plate (CLAUDE.md §5).
			violations.addIf(vehicleRepository.findByPlateNormalized(TextNormalizationUtils
					.normalizePlate(values.plate())).filter(other -> !other.getId().equals(id)).isPresent(),
					UniqueConstraint.VEHICLE_PLATE);
		}

		violations.required("brand", values.brand(), "Η μάρκα είναι υποχρεωτική.");
		listed(violations, "brand", values.brand(), stored == null ? null : stored.getBrand(),
				brand -> brands().contains(brand), "Επιλέξτε μάρκα από τη λίστα.");
		violations.required("model", values.model(), "Το μοντέλο είναι υποχρεωτικό.");
		violations.required("firstRegistration", values.firstRegistration(),
				"Η ημερομηνία 1ης άδειας είναι υποχρεωτική.");
		violations.required("category", values.category(), "Η κατηγορία είναι υποχρεωτική.");
		listed(violations, "category", values.category(), stored == null ? null : stored.getCategory(),
				VehicleValues::isCategory, "Επιλέξτε κατηγορία από τη λίστα.");
		violations.required("color", values.color(), "Το χρώμα είναι υποχρεωτικό.");
		listed(violations, "color", values.color(), stored == null ? null : stored.getColor(),
				VehicleValues::isColor, "Επιλέξτε χρώμα από τη λίστα.");
		listed(violations, "emissionStandard", values.emissionStandard(),
				stored == null ? null : stored.getEmissionStandard(), VehicleValues::isEmissionStandard,
				"Επιλέξτε Euro από τη λίστα.");
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

		violations.addIf(values.seats() != null && !isSeatCount(values.seats()),
				"seats", "Οι θέσεις πρέπει να είναι από 1 έως 99.");
		violations.addIf(values.co2() != null && values.co2() < 0, "co2", "Το CO2 δεν μπορεί να είναι αρνητικό.");
		violations.addIf(values.weightKg() != null && values.weightKg() <= 0,
				"weightKg", "Το βάρος πρέπει να είναι θετικός αριθμός.");
		violations.format("licensePostalCode", values.licensePostalCode(), POSTAL_CODE,
				"Ο Τ.Κ. πρέπει να έχει 5 ψηφία.");
		// Task 28. The plate as stored, without its dashes (cleaned); the VIN
		// and the Τ.Κ. are limited by their format already.
		violations.fitsColumn("plate", values.plate(), Vehicle.class);
		violations.fitsColumn("brand", values.brand(), Vehicle.class);
		violations.fitsColumn("model", values.model(), Vehicle.class);
		violations.fitsColumn("category", values.category(), Vehicle.class);
		violations.fitsColumn("color", values.color(), Vehicle.class);
		violations.fitsColumn("engineNumber", values.engineNumber(), Vehicle.class);
		violations.fitsColumn("emissionStandard", values.emissionStandard(), Vehicle.class);
		violations.fitsColumn("licenseStreet", values.licenseStreet(), Vehicle.class);
		violations.fitsColumn("licenseCity", values.licenseCity(), Vehicle.class);
		return violations;
	}

	/**
	 * Task 23a: a new or changed value must be one of the list. A value saved
	 * before the list, or imported from outside it, is kept while the clerk
	 * leaves it as it is (decision 7), so that correcting another field does
	 * not first need the right colour or category.
	 */
	private static void listed(Violations violations, String field, String value, String storedValue,
			Predicate<String> inList, String message) {
		violations.addIf(value != null && !value.equals(storedValue) && !inList.test(value), field, message);
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
