package gr.insuranceoffice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.DeletionPreviewDto;
import gr.insuranceoffice.dto.IntermediaryDto;
import gr.insuranceoffice.dto.PageDto;
import gr.insuranceoffice.dto.PolicyDto;
import gr.insuranceoffice.dto.PolicyFormDto;
import gr.insuranceoffice.dto.PolicyListDto;
import gr.insuranceoffice.dto.PolicyStatus;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.mapper.IntermediaryMapper;
import gr.insuranceoffice.mapper.PolicyMapper;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.security.Roles;

/**
 * The policy form (Task 11d) and the rules a policy must satisfy (SPEC §8).
 * No duration is assumed anywhere: six- and twelve-month policies both exist,
 * so the end date is always the clerk's.
 */
@Service
public class PolicyService {

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// NUMERIC(10,2): eight digits before the decimal point.
	private static final BigDecimal MAX_PREMIUM = new BigDecimal("100000000");

	private final PolicyRepository policyRepository;

	private final VehicleRepository vehicleRepository;

	private final IntermediaryRepository intermediaryRepository;

	private final OwnershipRepository ownershipRepository;

	private final PolicyMapper policyMapper;

	private final IntermediaryMapper intermediaryMapper;

	public PolicyService(PolicyRepository policyRepository, VehicleRepository vehicleRepository,
			IntermediaryRepository intermediaryRepository, OwnershipRepository ownershipRepository,
			PolicyMapper policyMapper, IntermediaryMapper intermediaryMapper) {
		this.policyRepository = policyRepository;
		this.vehicleRepository = vehicleRepository;
		this.intermediaryRepository = intermediaryRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyMapper = policyMapper;
		this.intermediaryMapper = intermediaryMapper;
	}

	/**
	 * An empty form for a new policy of the vehicle. The dates stay empty:
	 * the duration is never assumed.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@Transactional(readOnly = true)
	public PolicyFormDto newPolicy(Long vehicleId) {
		vehicle(vehicleId);
		return new PolicyFormDto(null, vehicleId, null, null, null, null, null, null, Boolean.FALSE, null, null);
	}

	/**
	 * The policy as the edit form shows it.
	 *
	 * @throws NotFoundException if the policy does not exist
	 */
	@Transactional(readOnly = true)
	public PolicyFormDto find(Long id) {
		return policyMapper.toFormDto(policy(id));
	}

	/**
	 * A new policy prefilled from this one (Task 12): same vehicle, company,
	 * intermediary, surcharge and premium, starting the day this one ends,
	 * since touching is not overlapping (Task 11d). The number is left empty,
	 * as it must be a new one, and so is the end: the duration is never
	 * assumed. Saving it creates a policy; this one stays as history.
	 *
	 * @throws NotFoundException if the policy does not exist
	 */
	@Transactional(readOnly = true)
	public PolicyFormDto renewal(Long id) {
		PolicyFormDto current = policyMapper.toFormDto(policy(id));
		return new PolicyFormDto(null, current.vehicleId(), null, current.insuranceCompany(),
				current.intermediaryId(), current.endDate(), null, current.premium(), current.surcharge(),
				current.surchargeType(), null);
	}

	/** The intermediaries the form offers (Task 11d: existing ones only). */
	@Transactional(readOnly = true)
	public List<IntermediaryDto> selectableIntermediaries(Long selectedId) {
		return intermediaryMapper.toDtoList(intermediaryRepository.findSelectable(selectedId));
	}

	/**
	 * A page of the policy list (Task 15), by end date, for one insurance
	 * company or for all. Every row carries its customer, as on the vehicle
	 * card (Task 13), found for the whole page in one query.
	 *
	 * @param insuranceCompany exact name; null or blank for every company
	 * @param page             from 1; a page past the end gives the last one
	 */
	@Transactional(readOnly = true)
	public PolicyListDto list(String insuranceCompany, SortDirection direction, int page) {
		String company = insuranceCompany == null || insuranceCompany.isBlank() ? null : insuranceCompany;
		PageDto<PolicyViewDto> policies = Paging.page(page, direction, List.of("endDate", "id"),
				pageable -> policyRepository.findPage(company, pageable), this::policyRows);
		return new PolicyListDto(policies, company, policyRepository.findInsuranceCompanies());
	}

	private List<PolicyViewDto> policyRows(List<Policy> policies) {
		if (policies.isEmpty()) {
			return List.of();
		}
		LocalDate today = LocalDate.now();
		Map<Long, List<Ownership>> ownerships = ownershipRepository
				.findPrimaryByVehicleIdsWithCustomer(
						policies.stream().map(policy -> policy.getVehicle().getId()).distinct().toList())
				.stream().collect(Collectors.groupingBy(ownership -> ownership.getVehicle().getId()));
		return policies.stream()
				.map(policy -> PolicyCustomers.attach(
						policyMapper.toViewDto(policy, PolicyStatus.of(policy.getStartDate(), policy.getEndDate(), today)),
						ownerships.getOrDefault(policy.getVehicle().getId(), List.of())))
				.toList();
	}

	/** The companies already on file, offered as suggestions so one is not spelled three ways. */
	@Transactional(readOnly = true)
	public List<String> knownInsuranceCompanies() {
		return policyRepository.findInsuranceCompanies();
	}

	/**
	 * @throws NotFoundException if the vehicle does not exist
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public PolicyDto create(Long vehicleId, PolicyFormDto form) {
		Vehicle vehicle = vehicle(vehicleId);
		PolicyFormDto values = policyMapper.withBlanksAsNull(form);
		Violations violations = new Violations();
		BigDecimal premium = premium(values.premium(), violations);
		checkFields(values, vehicleId, null, violations);
		checkMobileRule(vehicleId, values, false, violations);
		violations.throwIfAny();

		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		apply(values, premium, policy);
		return policyMapper.toDto(policyRepository.saveAndFlush(policy));
	}

	/**
	 * @param form carries the version the clerk started editing from; its
	 *             vehicle is ignored, since a policy never moves to another
	 * @throws NotFoundException if the policy no longer exists
	 * @throws ObjectOptimisticLockingFailureException if someone else saved it
	 *             in the meantime
	 * @throws BusinessException if a field breaks a rule
	 */
	@Transactional
	public PolicyDto update(Long id, PolicyFormDto form) {
		Policy policy = policy(id);
		if (!Objects.equals(form.version(), policy.getVersion())) {
			throw new ObjectOptimisticLockingFailureException(Policy.class, id);
		}

		Long vehicleId = policy.getVehicle().getId();
		PolicyFormDto values = policyMapper.withBlanksAsNull(form);
		Violations violations = new Violations();
		BigDecimal premium = premium(values.premium(), violations);
		checkFields(values, vehicleId, id, violations);
		checkMobileRule(vehicleId, values, current(policy.getStartDate(), policy.getEndDate()), violations);
		violations.throwIfAny();

		apply(values, premium, policy);
		// Flushed here so that a concurrent change fails inside this call and
		// the returned version is the new one.
		policyRepository.flush();
		return policyMapper.toDto(policy);
	}

	/**
	 * What deleting the policy means (Task 11e). Nothing hangs off a policy.
	 *
	 * @throws NotFoundException if the policy does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional(readOnly = true)
	public DeletionPreviewDto deletionPreview(Long id) {
		Policy policy = policy(id);
		return new DeletionPreviewDto("Το συμβόλαιο " + policy.getPolicyNumber() + " του οχήματος "
				+ policy.getVehicle().getPlate() + " (" + policy.getStartDate().format(GREEK_DATE) + " – "
				+ policy.getEndDate().format(GREEK_DATE) + ")", List.of(), List.of(), policy.getVehicle().getId());
	}

	/**
	 * Hard delete (DECISIONS §4), written to audit_log.
	 *
	 * @return the vehicle the policy belonged to
	 * @throws NotFoundException if the policy does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional
	public Long delete(Long id) {
		Policy policy = policy(id);
		Long vehicleId = policy.getVehicle().getId();
		policyRepository.delete(policy);
		return vehicleId;
	}

	private void apply(PolicyFormDto values, BigDecimal premium, Policy policy) {
		policyMapper.updateEntity(values, policy);
		policy.setPremium(premium);
		policy.setSurcharge(hasSurcharge(values));
		policy.setIntermediary(values.intermediaryId() == null ? null
				: intermediaryRepository.getReferenceById(values.intermediaryId()));
	}

	private void checkFields(PolicyFormDto values, Long vehicleId, Long policyId, Violations violations) {
		violations.required("policyNumber", values.policyNumber(), "Ο αριθμός συμβολαίου είναι υποχρεωτικός.");
		violations.addIf(values.policyNumber() != null && values.policyNumber().length() > 30,
				"policyNumber", "Ο αριθμός συμβολαίου έχει το πολύ 30 χαρακτήρες.");
		if (values.policyNumber() != null) {
			violations.addIf(policyRepository.findByPolicyNumber(values.policyNumber())
					.filter(other -> !other.getId().equals(policyId)).isPresent(), UniqueConstraint.POLICY_NUMBER);
		}
		violations.required("insuranceCompany", values.insuranceCompany(), "Η ασφαλιστική εταιρεία είναι υποχρεωτική.");

		if (values.intermediaryId() != null) {
			violations.addIf(!intermediaryRepository.existsById(values.intermediaryId()),
					"intermediaryId", "Ο διαμεσολαβών δεν υπάρχει πια· επιλέξτε ξανά.");
		}

		violations.required("startDate", values.startDate(), "Η ημερομηνία έναρξης είναι υποχρεωτική.");
		violations.required("endDate", values.endDate(), "Η ημερομηνία λήξης είναι υποχρεωτική.");
		if (values.startDate() != null && values.endDate() != null) {
			if (!values.endDate().isAfter(values.startDate())) {
				violations.add("endDate", "Η λήξη πρέπει να είναι μετά την έναρξη.");
			} else {
				// SPEC §8: a vehicle is insured by one policy at a time. Touching
				// is allowed (Task 11d).
				for (Policy other : policyRepository.findOverlapping(vehicleId, policyId, values.startDate(),
						values.endDate())) {
					violations.add(null, "Επικαλύπτεται με το συμβόλαιο " + other.getPolicyNumber() + " ("
							+ other.getStartDate().format(GREEK_DATE) + " – " + other.getEndDate().format(GREEK_DATE)
							+ "). Ένα συμβόλαιο μπορεί να αρχίζει την ημέρα που λήγει το προηγούμενο.");
				}
			}
		}

		// The type only makes sense with a surcharge, and a surcharge needs one.
		if (hasSurcharge(values)) {
			violations.addIf(values.surchargeType() == null || Arrays.stream(Policy.SurchargeType.values())
					.noneMatch(type -> type.name().equals(values.surchargeType())),
					"surchargeType", "Επιλέξτε τον τύπο του επασφαλίστρου.");
		} else {
			violations.addIf(values.surchargeType() != null, "surchargeType",
					"Τύπος επασφαλίστρου μόνο όταν υπάρχει επασφάλιστρο· σημειώστε το ή αφήστε τον τύπο κενό.");
		}
	}

	/**
	 * DECISIONS §1, from the policy side (NOTES "Mobile rule from the other
	 * side"): a vehicle whose primary owner has no mobile cannot get a policy
	 * in force today. An edit of a policy that was already in force is left
	 * alone, so old data does not block every correction.
	 */
	private void checkMobileRule(Long vehicleId, PolicyFormDto values, boolean wasCurrent, Violations violations) {
		if (wasCurrent || values.startDate() == null || values.endDate() == null
				|| !current(values.startDate(), values.endDate())) {
			return;
		}
		ownershipRepository.findCurrentPrimaryOwners(List.of(vehicleId)).stream()
				.map(Ownership::getCustomer)
				.filter(owner -> owner.getMobile() == null)
				.findFirst()
				.ifPresent(owner -> violations.add(null, "Ο κύριος ιδιοκτήτης " + name(owner)
						+ " δεν έχει κινητό, και το συμβόλαιο θα ίσχυε από σήμερα. Συμπληρώστε πρώτα το κινητό του."));
	}

	/**
	 * Reads the premium as typed: "180,50", "180.50", "1.234,50" and
	 * "180,00 €" are all accepted, and it is stored as a number, never as
	 * text (SPEC §8).
	 */
	private static BigDecimal premium(String typed, Violations violations) {
		if (typed == null) {
			violations.add("premium", "Το ασφάλιστρο είναι υποχρεωτικό.");
			return null;
		}
		String text = typed.replace("€", "").replaceAll("\\s", "");
		// With a comma present, the comma is the decimal point and dots group
		// thousands; without one, a dot is the decimal point.
		if (text.contains(",")) {
			text = text.replace(".", "").replace(',', '.');
		}
		BigDecimal premium;
		try {
			premium = new BigDecimal(text);
		} catch (NumberFormatException e) {
			violations.add("premium", "Συμπληρώστε ποσό, π.χ. 180,50.");
			return null;
		}
		if (premium.signum() <= 0 || premium.compareTo(MAX_PREMIUM) >= 0) {
			violations.add("premium", "Το ασφάλιστρο πρέπει να είναι θετικό ποσό.");
			return null;
		}
		if (premium.stripTrailingZeros().scale() > 2) {
			violations.add("premium", "Το ασφάλιστρο έχει το πολύ δύο δεκαδικά.");
			return null;
		}
		return premium;
	}

	private static boolean hasSurcharge(PolicyFormDto values) {
		return Boolean.TRUE.equals(values.surcharge());
	}

	// «Τρέχον» = CURRENT_DATE BETWEEN start_date AND end_date (DATA_MODEL).
	private static boolean current(LocalDate start, LocalDate end) {
		LocalDate today = LocalDate.now();
		return !today.isBefore(start) && !today.isAfter(end);
	}

	private Vehicle vehicle(Long vehicleId) {
		return vehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε."));
	}

	private Policy policy(Long id) {
		return policyRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Το συμβόλαιο δεν βρέθηκε."));
	}

	private static String name(Customer customer) {
		return customer.getFirstName() == null ? customer.getLastName()
				: customer.getLastName() + " " + customer.getFirstName();
	}

}
