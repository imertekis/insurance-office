package gr.insuranceoffice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.DeletionPreviewDto;
import gr.insuranceoffice.dto.OwnersFormDto;
import gr.insuranceoffice.dto.OwnersFormDto.OwnerRowDto;
import gr.insuranceoffice.dto.OwnersSubmissionDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.security.Roles;

/**
 * The ownership rule (SPEC §8, DATA_MODEL "ownership"): a vehicle's current
 * owners' shares sum to 100 and exactly one of them is primary. The database
 * cannot check a rule across rows, so every write path goes through here.
 * <p>
 * The form edits all current owners of a vehicle together (Task 11c). An
 * owner who is removed is closed with {@code to_date}, never deleted: a
 * transfer is an event with a date, and the vehicle card shows former owners.
 */
@Service
public class OwnershipService {

	private static final BigDecimal FULL = new BigDecimal(100);

	private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final OwnershipRepository ownershipRepository;

	private final VehicleRepository vehicleRepository;

	private final CustomerRepository customerRepository;

	private final PolicyRepository policyRepository;

	public OwnershipService(OwnershipRepository ownershipRepository, VehicleRepository vehicleRepository,
			CustomerRepository customerRepository, PolicyRepository policyRepository) {
		this.ownershipRepository = ownershipRepository;
		this.vehicleRepository = vehicleRepository;
		this.customerRepository = customerRepository;
		this.policyRepository = policyRepository;
	}

	/** One current owner's share of a vehicle. */
	public record Share(BigDecimal percentage, boolean primary) {
	}

	/**
	 * @param shares all current owners of one vehicle
	 * @return what breaks the rule, in Greek; empty when the owners are valid
	 */
	public static List<String> checkCurrentOwners(List<Share> shares) {
		List<String> problems = new ArrayList<>();
		BigDecimal total = shares.stream().map(Share::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(FULL) != 0) {
			problems.add("τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν " + percent(total)
					+ " αντί για 100%");
		}
		long primaries = shares.stream().filter(Share::primary).count();
		if (primaries != 1) {
			problems.add("το όχημα πρέπει να έχει ακριβώς έναν κύριο ιδιοκτήτη, έχει " + primaries);
		}
		return problems;
	}

	/**
	 * The form as it stands in the database: the current owners, the transfer
	 * date set to today.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@Transactional(readOnly = true)
	public OwnersFormDto ownersForm(Long vehicleId) {
		Vehicle vehicle = vehicle(vehicleId);
		List<Ownership> current = ownershipRepository.findCurrentByVehicleIdWithCustomer(vehicleId);
		Long primary = current.stream().filter(Ownership::isPrimary).map(o -> o.getCustomer().getId())
				.findFirst().orElse(null);
		List<OwnerRowDto> rows = current.stream()
				.map(ownership -> row(ownership.getCustomer(), typed(ownership.getPercentage())))
				.toList();
		return new OwnersFormDto(vehicleId, vehicle.getPlate(), vehicle.getVersion(), LocalDate.now(), primary,
				rows, false);
	}

	/**
	 * The form as the clerk left it, before saving: after adding or removing a
	 * row, or when saving failed. Customers are looked up for their names;
	 * the shares stay as typed. It says whether the rows differ from the
	 * owners in the database, so the page can warn before they are lost
	 * (Task 16f-2).
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 */
	@Transactional(readOnly = true)
	public OwnersFormDto ownersForm(Long vehicleId, OwnersSubmissionDto submission) {
		Vehicle vehicle = vehicle(vehicleId);
		Map<Long, Customer> customers = customers(submission.customerIds());
		List<OwnerRowDto> rows = new ArrayList<>();
		for (int i = 0; i < submission.customerIds().size(); i++) {
			Customer customer = customers.get(submission.customerIds().get(i));
			if (customer != null) {
				rows.add(row(customer, submission.percentages().get(i)));
			}
		}
		return new OwnersFormDto(vehicleId, vehicle.getPlate(), submission.vehicleVersion(),
				submission.transferDate(), submission.primaryCustomerId(), rows,
				differsFromSaved(vehicleId, submission));
	}

	/**
	 * Task 16f-2: whether the rows of the form are not what is saved: another
	 * set of customers, or another share or primary owner for one of them.
	 * Shares are compared as numbers ("50", "50,00" and "50.0" are the same);
	 * one that cannot be read differs. The transfer date alone does not
	 * count: without a change of rows it saves nothing. A customer twice
	 * always differs: the saved owners have one current row per customer
	 * (REVIEW-08).
	 */
	private boolean differsFromSaved(Long vehicleId, OwnersSubmissionDto submission) {
		if (new HashSet<>(submission.customerIds()).size() != submission.customerIds().size()) {
			return true;
		}
		List<Ownership> saved = ownershipRepository.findCurrentByVehicleIdWithCustomer(vehicleId);
		if (saved.size() != submission.customerIds().size()) {
			return true;
		}
		Long savedPrimary = saved.stream().filter(Ownership::isPrimary).map(o -> o.getCustomer().getId())
				.findFirst().orElse(null);
		if (!Objects.equals(savedPrimary, submission.primaryCustomerId())) {
			return true;
		}
		Map<Long, BigDecimal> savedShares = new HashMap<>();
		saved.forEach(ownership -> savedShares.put(ownership.getCustomer().getId(), ownership.getPercentage()));
		for (int i = 0; i < submission.customerIds().size(); i++) {
			BigDecimal savedShare = savedShares.get(submission.customerIds().get(i));
			BigDecimal typedShare = number(submission.percentages().get(i));
			if (savedShare == null || typedShare == null || savedShare.compareTo(typedShare) != 0) {
				return true;
			}
		}
		return false;
	}

	// A share as typed, with a comma or a point; null when it is not a number.
	private static BigDecimal number(String typed) {
		if (typed == null || typed.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(typed.strip().replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Replaces the vehicle's current owners with the submitted ones, in one
	 * transaction. Owners who stay keep their row with the new share; owners
	 * who leave are closed on the transfer date; new owners start on it.
	 *
	 * @throws NotFoundException if the vehicle does not exist
	 * @throws ObjectOptimisticLockingFailureException if the vehicle, or its
	 *             owners, changed since the form was opened
	 * @throws BusinessException with a message per share that cannot be read
	 *             ({@code percentages[i]}) and, at the top, whatever breaks
	 *             the rule for the owners as a whole
	 */
	@Transactional
	public void saveOwners(Long vehicleId, OwnersSubmissionDto submission) {
		Vehicle vehicle = vehicleRepository.findForOwnersChange(vehicleId)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν υπάρχει πια."));
		if (!Objects.equals(submission.vehicleVersion(), vehicle.getVersion())) {
			throw new ObjectOptimisticLockingFailureException(Vehicle.class, vehicleId);
		}

		List<Ownership> current = ownershipRepository.findCurrentByVehicleIdWithCustomer(vehicleId);
		Map<Long, Ownership> currentByCustomer = current.stream()
				// The ownership rule allows one current row per customer; the form
				// never fails over data that breaks it.
				.collect(Collectors.toMap(o -> o.getCustomer().getId(), Function.identity(), (first, second) -> first));
		Map<Long, Customer> customers = customers(submission.customerIds());
		LocalDate today = LocalDate.now();
		Violations violations = new Violations();

		// Each share on its own: readable, above 0, at most 100, two decimals.
		List<BigDecimal> shares = new ArrayList<>();
		for (int i = 0; i < submission.percentages().size(); i++) {
			shares.add(share(submission.percentages().get(i), "percentages[" + i + "]", violations));
		}

		Set<Long> seen = new HashSet<>();
		for (Long customerId : submission.customerIds()) {
			violations.addIf(!seen.add(customerId), null, "Ο ίδιος πελάτης εμφανίζεται δύο φορές.");
			violations.addIf(!customers.containsKey(customerId), null,
					"Ένας από τους πελάτες δεν υπάρχει πια· ανοίξτε ξανά τη φόρμα.");
		}

		// The owners as a whole, once every share could be read.
		if (!shares.contains(null)) {
			List<Share> proposed = new ArrayList<>();
			for (int i = 0; i < shares.size(); i++) {
				proposed.add(new Share(shares.get(i),
						submission.customerIds().get(i).equals(submission.primaryCustomerId())));
			}
			checkCurrentOwners(proposed).forEach(problem -> violations.add(null, capitalized(problem) + "."));
		}

		// DECISIONS §1: the office must be able to reach whoever answers for an
		// insured vehicle.
		Customer primary = customers.get(submission.primaryCustomerId());
		if (primary != null && primary.getMobile() == null && policyRepository.isInsuredOn(vehicleId, today)) {
			violations.add(null, "Ο πελάτης " + name(primary) + " δεν έχει κινητό και δεν μπορεί να γίνει κύριος "
					+ "ιδιοκτήτης οχήματος με τρέχον συμβόλαιο. Συμπληρώστε πρώτα το κινητό του.");
		}

		// A transfer is an event with a date (Task 11c): it is needed as soon as
		// someone leaves or joins.
		List<Ownership> leaving = current.stream()
				.filter(ownership -> !submission.customerIds().contains(ownership.getCustomer().getId()))
				.toList();
		boolean joining = submission.customerIds().stream().anyMatch(id -> !currentByCustomer.containsKey(id));
		LocalDate transferDate = submission.transferDate();
		if (!leaving.isEmpty() || joining) {
			violations.required("transferDate", transferDate, "Συμπληρώστε την ημερομηνία μεταβίβασης.");
		}
		if (transferDate != null) {
			violations.addIf(transferDate.isAfter(today), "transferDate",
					"Η ημερομηνία μεταβίβασης δεν μπορεί να είναι μελλοντική.");
			for (Ownership ownership : leaving) {
				violations.addIf(ownership.getFromDate() != null && transferDate.isBefore(ownership.getFromDate()),
						"transferDate", "Ο πελάτης " + name(ownership.getCustomer()) + " έγινε ιδιοκτήτης στις "
								+ ownership.getFromDate() + ", μετά την ημερομηνία μεταβίβασης.");
			}
		}
		violations.throwIfAny();

		for (Ownership ownership : leaving) {
			ownership.setToDate(transferDate);
		}
		for (int i = 0; i < submission.customerIds().size(); i++) {
			Long customerId = submission.customerIds().get(i);
			Ownership ownership = currentByCustomer.get(customerId);
			if (ownership == null) {
				ownership = new Ownership();
				ownership.setVehicle(vehicle);
				ownership.setCustomer(customers.get(customerId));
				ownership.setFromDate(transferDate);
			}
			ownership.setPercentage(shares.get(i));
			ownership.setPrimary(customerId.equals(submission.primaryCustomerId()));
			ownershipRepository.save(ownership);
		}
	}

	/**
	 * What deleting one ownership row means (Task 11e). Only a closed row can
	 * go, to correct history, e.g. a transfer recorded by mistake; current
	 * owners change through the form, so the 100% rule cannot be broken here.
	 *
	 * @throws NotFoundException if the ownership does not exist
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional(readOnly = true)
	public DeletionPreviewDto deletionPreview(Long ownershipId) {
		Ownership ownership = ownershipRepository.findById(ownershipId)
				.orElseThrow(() -> new NotFoundException("Η ιδιοκτησία δεν βρέθηκε."));
		String period = ownership.getToDate() == null ? "τρέχουσα"
				: "έως " + ownership.getToDate().format(GREEK_DATE);
		List<String> blockers = ownership.getToDate() == null
				? List.of("Είναι τρέχουσα ιδιοκτησία· οι τρέχοντες ιδιοκτήτες αλλάζουν από τη φόρμα ιδιοκτητών.")
				: List.of();
		return new DeletionPreviewDto("Η ιδιοκτησία του " + name(ownership.getCustomer()) + " στο όχημα "
				+ ownership.getVehicle().getPlate() + " (" + period + ")", List.of(), blockers,
				ownership.getVehicle().getId());
	}

	/**
	 * Hard delete of a closed ownership row (DECISIONS §4), written to
	 * audit_log.
	 *
	 * @return the vehicle the ownership belonged to
	 * @throws NotFoundException if the ownership does not exist
	 * @throws BusinessException if it is a current ownership
	 */
	@PreAuthorize(Roles.ADMINISTRATOR_ONLY)
	@Transactional
	public Long delete(Long ownershipId) {
		DeletionPreviewDto preview = deletionPreview(ownershipId);
		if (preview.isBlocked()) {
			throw new BusinessException(preview.blockers().stream()
					.map(blocker -> new BusinessException.Violation(null, blocker)).toList());
		}
		ownershipRepository.deleteById(ownershipId);
		return preview.vehicleId();
	}

	// Reads one share as typed; "33,33" and "33.33" are the same.
	private static BigDecimal share(String typed, String field, Violations violations) {
		if (typed == null || typed.isBlank()) {
			violations.add(field, "Συμπληρώστε το ποσοστό.");
			return null;
		}
		BigDecimal share = number(typed);
		if (share == null) {
			violations.add(field, "Συμπληρώστε αριθμό, π.χ. 50 ή 33,33.");
			return null;
		}
		if (share.signum() <= 0 || share.compareTo(FULL) > 0) {
			violations.add(field, "Το ποσοστό πρέπει να είναι πάνω από 0 και έως 100.");
			return null;
		}
		if (share.stripTrailingZeros().scale() > 2) {
			violations.add(field, "Το ποσοστό έχει το πολύ δύο δεκαδικά.");
			return null;
		}
		return share;
	}

	private Vehicle vehicle(Long vehicleId) {
		return vehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new NotFoundException("Το όχημα δεν βρέθηκε."));
	}

	private Map<Long, Customer> customers(List<Long> ids) {
		Map<Long, Customer> customers = new LinkedHashMap<>();
		customerRepository.findAllById(ids).forEach(customer -> customers.put(customer.getId(), customer));
		return customers;
	}

	private static OwnerRowDto row(Customer customer, String percentage) {
		return new OwnerRowDto(customer.getId(), customer.getLastName(), customer.getFirstName(),
				customer.getTaxId(), customer.getMobile(), percentage);
	}

	// 50.00 is shown as 50, 33.33 as 33,33.
	private static String typed(BigDecimal percentage) {
		return percentage.stripTrailingZeros().toPlainString().replace('.', ',');
	}

	private static String name(Customer customer) {
		return customer.getFirstName() == null ? customer.getLastName()
				: customer.getLastName() + " " + customer.getFirstName();
	}

	private static String capitalized(String text) {
		return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}

	private static String percent(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
	}

}
