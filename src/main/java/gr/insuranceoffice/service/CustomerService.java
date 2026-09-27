package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.CustomerDetailDto;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.DeletionPreviewDto;
import gr.insuranceoffice.dto.PageDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.SavedCustomerDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.mapper.CustomerMapper;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.security.Roles;
import gr.insuranceoffice.service.BusinessException.Violation;

@Service
public class CustomerService {

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// SPEC §8.
	private static final Pattern MOBILE = Pattern.compile("6\\d{9}");
	private static final Pattern LANDLINE = Pattern.compile("2\\d{9}");
	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");
	// Loose on purpose: something@something, without spaces.
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");

	private final CustomerRepository customerRepository;

	private final OwnershipRepository ownershipRepository;

	private final PolicyRepository policyRepository;

	private final CustomerMapper customerMapper;

	private final OwnershipMapper ownershipMapper;

	private final PolicyMapper policyMapper;

	private final HitAssembler hitAssembler;

	public CustomerService(CustomerRepository customerRepository, OwnershipRepository ownershipRepository,
			PolicyRepository policyRepository, CustomerMapper customerMapper, OwnershipMapper ownershipMapper,
			PolicyMapper policyMapper, HitAssembler hitAssembler) {
		this.customerRepository = customerRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.customerMapper = customerMapper;
		this.ownershipMapper = ownershipMapper;
		this.policyMapper = policyMapper;
		this.hitAssembler = hitAssembler;
	}

	/**
	 * A page of the customer list (Task 15), by name in Greek alphabetical
	 * order: last name first, accents and case not counting, so «Άγγελος»
	 * sorts with Α. The order is the database's, from the {@code name_sort}
	 * column of V5.
	 *
	 * @param page from 1; a page past the end gives the last one
	 */
	@Transactional(readOnly = true)
	public PageDto<CustomerHit> list(SortDirection direction, int page) {
		return Paging.page(page, direction, List.of("nameSort", "id"),
				pageable -> customerRepository.findAll(pageable), hitAssembler::customerHits);
	}

	/**
	 * The customer's own fields, for the edit form.
	 *
	 * @throws NotFoundException if the customer does not exist
	 */
	@Transactional(readOnly = true)
	public CustomerDto find(Long id) {
		return customerRepository.findById(id)
				.map(customerMapper::toDto)
				.orElseThrow(() -> new NotFoundException("Ο πελάτης δεν βρέθηκε."));
	}

	/**
	 * The customer with the vehicles they own and the policies of those that
	 * started while they owned them (SPEC §7.3): three queries, whatever the
	 * number of rows. A vehicle's later owner's policies are theirs, not this
	 * customer's; see {@link #policyViews}.
	 *
	 * @throws NotFoundException if the customer does not exist
	 */
	@Transactional(readOnly = true)
	public CustomerDetailDto findDetail(Long id) {
		return customerRepository.findById(id)
				.map(customer -> {
					List<Ownership> ownerships = ownershipRepository.findByCustomerIdWithVehicle(id);
					return new CustomerDetailDto(customerMapper.toDto(customer),
							ownershipMapper.toOwnedVehicleDtoList(ownerships),
							policyViews(policyRepository.findByOwnerWithVehicleAndIntermediary(id), ownerships));
				})
				.orElseThrow(() -> new NotFoundException("Ο πελάτης δεν βρέθηκε."));
	}

	/**
	 * The policies that started within one of the customer's ownerships of
	 * the vehicle, by the rule of Task 13: to_date excluded, an empty date
	 * open. So a policy the next owner took out after a sale is not listed,
	 * and one that starts on the transfer day is the buyer's. A co-owner has
	 * an ownership too and sees the vehicle's policies. Any ownership will
	 * do, as a vehicle can be sold and bought back.
	 */
	private List<PolicyViewDto> policyViews(List<Policy> policies, List<Ownership> ownerships) {
		LocalDate today = LocalDate.now();
		Map<Long, List<Ownership>> ownershipsByVehicle = ownerships.stream()
				.collect(Collectors.groupingBy(ownership -> ownership.getVehicle().getId()));
		return policies.stream()
				.filter(policy -> ownershipsByVehicle.getOrDefault(policy.getVehicle().getId(), List.of()).stream()
						.anyMatch(ownership -> PolicyCustomers.covers(ownership, policy.getStartDate())))
				.map(policy -> policyMapper.toViewDto(policy,
						PolicyStatus.of(policy.getStartDate(), policy.getEndDate(), today)))
				.toList();
	}

	/**
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public SavedCustomerDto create(CustomerDto dto) {
		CustomerDto values = customerMapper.withBlanksAsNull(dto);
		checkFields(values, null).throwIfAny();

		Customer customer = new Customer();
		customerMapper.updateEntity(values, customer);
		return saved(customerRepository.saveAndFlush(customer));
	}

	/**
	 * @param dto carries the version the clerk started editing from
	 * @throws NotFoundException if the customer no longer exists
	 * @throws ObjectOptimisticLockingFailureException if someone else saved
	 *             the customer in the meantime
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public SavedCustomerDto update(Long id, CustomerDto dto) {
		Customer customer = customerRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Ο πελάτης δεν υπάρχει πια."));
		// Hibernate compares the version it loaded, not one set on the entity,
		// so a form based on an older version has to be caught here.
		if (!Objects.equals(dto.version(), customer.getVersion())) {
			throw new ObjectOptimisticLockingFailureException(Customer.class, id);
		}

		CustomerDto values = customerMapper.withBlanksAsNull(dto);
		Violations violations = checkFields(values, id);
		// DECISIONS §1: the office must be able to reach whoever answers for an
		// insured vehicle. Only an existing customer can own one.
		violations.addIf(values.mobile() == null
				&& ownershipRepository.isCurrentPrimaryOwnerOfInsuredVehicle(id, LocalDate.now()),
				"mobile", "Το κινητό είναι υποχρεωτικό για τον κύριο ιδιοκτήτη οχήματος με τρέχον συμβόλαιο.");
		violations.throwIfAny();

		customerMapper.updateEntity(values, customer);
		// Flushed here so that a concurrent change fails inside this call and
		// the returned version is the new one.
		customerRepository.flush();
		return saved(customer);
	}

	/**
	 * What deleting the customer would take with it (Task 11e). Blocked while
	 * they own a vehicle: deleting them would leave it below 100% or without
	 * a primary owner, so its owners have to change first.
	 *
	 * @throws NotFoundException if the customer does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional(readOnly = true)
	public DeletionPreviewDto deletionPreview(Long id) {
		Customer customer = customerRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Ο πελάτης δεν βρέθηκε."));
		List<String> alsoDeleted = new ArrayList<>();
		List<String> blockers = new ArrayList<>();
		for (Ownership ownership : ownershipRepository.findByCustomerIdWithVehicle(id)) {
			String plate = ownership.getVehicle().getPlate();
			if (ownership.getToDate() == null) {
				blockers.add("Είναι τρέχων ιδιοκτήτης του οχήματος " + plate
						+ "· αλλάξτε πρώτα τους ιδιοκτήτες του.");
			} else {
				alsoDeleted.add("Η παλιά ιδιοκτησία του οχήματος " + plate + " (έως "
						+ ownership.getToDate().format(GREEK_DATE) + ")");
			}
		}
		return new DeletionPreviewDto("Ο πελάτης " + name(customer), alsoDeleted, blockers, null);
	}

	/**
	 * Hard delete (DECISIONS §4): the customer and their former ownerships go,
	 * each written to audit_log, from where they can be put back.
	 *
	 * @throws NotFoundException if the customer does not exist
	 * @throws BusinessException while the customer still owns a vehicle
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional
	public void delete(Long id) {
		DeletionPreviewDto preview = deletionPreview(id);
		if (preview.isBlocked()) {
			throw new BusinessException(preview.blockers().stream()
					.map(blocker -> new Violation(null, blocker)).toList());
		}
		// Loaded, so the ownerships are removed through JPA and logged (Task 6).
		customerRepository.delete(customerRepository.findById(id).orElseThrow());
	}

	/**
	 * ΑΦΜ check digit (DATA_MODEL "Έλεγχος ΑΦΜ"): the first 8 digits weighted
	 * 2⁸…2¹, summed, mod 11, mod 10, give the 9th.
	 */
	public static boolean isValidTaxId(String taxId) {
		if (taxId == null || !taxId.matches("\\d{9}")) {
			return false;
		}
		int sum = 0;
		for (int i = 0; i < 8; i++) {
			sum += (taxId.charAt(i) - '0') << (8 - i);
		}
		return sum % 11 % 10 == taxId.charAt(8) - '0';
	}

	private Violations checkFields(CustomerDto values, Long id) {
		Violations violations = new Violations();
		violations.required("lastName", values.lastName(), "Το επώνυμο είναι υποχρεωτικό.");
		violations.addIf(values.entityType() == null || Arrays.stream(Customer.EntityType.values())
				.noneMatch(type -> type.name().equals(values.entityType())),
				"entityType", "Επιλέξτε φυσικό ή νομικό πρόσωπο.");
		if (values.taxId() != null) {
			if (!isValidTaxId(values.taxId())) {
				violations.add("taxId", "Μη έγκυρο ΑΦΜ: 9 ψηφία με σωστό ψηφίο ελέγχου.");
			} else {
				violations.addIf(customerRepository.findByTaxId(values.taxId())
						.filter(other -> !other.getId().equals(id)).isPresent(), UniqueConstraint.CUSTOMER_TAX_ID);
			}
		}
		violations.format("mobile", values.mobile(), MOBILE,
				"Το κινητό πρέπει να έχει 10 ψηφία και να αρχίζει από 6.");
		violations.format("phone", values.phone(), LANDLINE,
				"Το σταθερό πρέπει να έχει 10 ψηφία και να αρχίζει από 2.");
		violations.format("postalCode", values.postalCode(), POSTAL_CODE, "Ο Τ.Κ. πρέπει να έχει 5 ψηφία.");
		violations.format("email", values.email(), EMAIL, "Μη έγκυρο email.");
		// Task 28. ΑΦΜ, phones and Τ.Κ. are limited by their format already;
		// the notes are TEXT, without a limit.
		violations.fitsColumn("lastName", values.lastName(), Customer.class);
		violations.fitsColumn("firstName", values.firstName(), Customer.class);
		violations.fitsColumn("fatherName", values.fatherName(), Customer.class);
		violations.fitsColumn("taxOffice", values.taxOffice(), Customer.class);
		violations.fitsColumn("street", values.street(), Customer.class);
		violations.fitsColumn("city", values.city(), Customer.class);
		violations.fitsColumn("email", values.email(), Customer.class);
		return violations;
	}

	private static String name(Customer customer) {
		return customer.getFirstName() == null ? customer.getLastName()
				: customer.getLastName() + " " + customer.getFirstName();
	}

	// DECISIONS §2: a missing ΑΦΜ never blocks saving, but the clerk is told.
	private SavedCustomerDto saved(Customer customer) {
		List<String> warnings = customer.getTaxId() == null
				? List.of("Λείπει το ΑΦΜ του πελάτη.")
				: List.of();
		return new SavedCustomerDto(customerMapper.toDto(customer), warnings);
	}

}
