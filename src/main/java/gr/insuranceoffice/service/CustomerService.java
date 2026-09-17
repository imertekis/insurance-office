package gr.insuranceoffice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.CustomerDetailDto;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.SavedCustomerDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.mapper.CustomerMapper;
import gr.insuranceoffice.mapper.OwnershipMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.service.BusinessException.Violation;

@Service
public class CustomerService {

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

	public CustomerService(CustomerRepository customerRepository, OwnershipRepository ownershipRepository,
			PolicyRepository policyRepository, CustomerMapper customerMapper, OwnershipMapper ownershipMapper,
			PolicyMapper policyMapper) {
		this.customerRepository = customerRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.customerMapper = customerMapper;
		this.ownershipMapper = ownershipMapper;
		this.policyMapper = policyMapper;
	}

	/**
	 * The customer with the vehicles they own and the policies of all of them
	 * (SPEC §7.3): three queries, whatever the number of rows.
	 *
	 * @throws NotFoundException if the customer does not exist
	 */
	@Transactional(readOnly = true)
	public CustomerDetailDto findDetail(Long id) {
		return customerRepository.findById(id)
				.map(customer -> new CustomerDetailDto(customerMapper.toDto(customer),
						ownershipMapper.toOwnedVehicleDtoList(ownershipRepository.findByCustomerIdWithVehicle(id)),
						policyViews(policyRepository.findByOwnerWithVehicleAndIntermediary(id))))
				.orElseThrow(() -> new NotFoundException("Ο πελάτης δεν βρέθηκε."));
	}

	private List<PolicyViewDto> policyViews(List<Policy> policies) {
		LocalDate today = LocalDate.now();
		return policies.stream()
				.map(policy -> policyMapper.toViewDto(policy, PolicyStatus.of(policy.getEndDate(), today)))
				.toList();
	}

	/**
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public SavedCustomerDto create(CustomerDto dto) {
		CustomerDto values = customerMapper.withBlanksAsNull(dto);
		List<Violation> violations = checkFields(values, null);
		throwIfAny(violations);

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
		List<Violation> violations = checkFields(values, id);
		// DECISIONS §1: the office must be able to reach whoever answers for an
		// insured vehicle. Only an existing customer can own one.
		if (values.mobile() == null
				&& ownershipRepository.isCurrentPrimaryOwnerOfInsuredVehicle(id, LocalDate.now())) {
			violations.add(new Violation("mobile",
					"Το κινητό είναι υποχρεωτικό για τον κύριο ιδιοκτήτη οχήματος με τρέχον συμβόλαιο."));
		}
		throwIfAny(violations);

		customerMapper.updateEntity(values, customer);
		// Flushed here so that a concurrent change fails inside this call and
		// the returned version is the new one.
		customerRepository.flush();
		return saved(customer);
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

	private List<Violation> checkFields(CustomerDto values, Long id) {
		List<Violation> violations = new ArrayList<>();
		if (values.lastName() == null) {
			violations.add(new Violation("lastName", "Το επώνυμο είναι υποχρεωτικό."));
		}
		if (values.entityType() == null || Arrays.stream(Customer.EntityType.values())
				.noneMatch(type -> type.name().equals(values.entityType()))) {
			violations.add(new Violation("entityType", "Επιλέξτε φυσικό ή νομικό πρόσωπο."));
		}
		if (values.taxId() != null) {
			if (!isValidTaxId(values.taxId())) {
				violations.add(new Violation("taxId", "Μη έγκυρο ΑΦΜ: 9 ψηφία με σωστό ψηφίο ελέγχου."));
			} else if (customerRepository.findByTaxId(values.taxId())
					.filter(other -> !other.getId().equals(id)).isPresent()) {
				violations.add(new Violation("taxId", "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ."));
			}
		}
		check(violations, "mobile", values.mobile(), MOBILE, "Το κινητό πρέπει να έχει 10 ψηφία και να αρχίζει από 6.");
		check(violations, "phone", values.phone(), LANDLINE, "Το σταθερό πρέπει να έχει 10 ψηφία και να αρχίζει από 2.");
		check(violations, "postalCode", values.postalCode(), POSTAL_CODE, "Ο Τ.Κ. πρέπει να έχει 5 ψηφία.");
		check(violations, "email", values.email(), EMAIL, "Μη έγκυρο email.");
		return violations;
	}

	// Optional fields: checked only when filled in.
	private static void check(List<Violation> violations, String field, String value, Pattern pattern,
			String message) {
		if (value != null && !pattern.matcher(value).matches()) {
			violations.add(new Violation(field, message));
		}
	}

	private static void throwIfAny(List<Violation> violations) {
		if (!violations.isEmpty()) {
			throw new BusinessException(violations);
		}
	}

	// DECISIONS §2: a missing ΑΦΜ never blocks saving, but the clerk is told.
	private SavedCustomerDto saved(Customer customer) {
		List<String> warnings = customer.getTaxId() == null
				? List.of("Λείπει το ΑΦΜ του πελάτη.")
				: List.of();
		return new SavedCustomerDto(customerMapper.toDto(customer), warnings);
	}

}
