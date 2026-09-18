package gr.insuranceoffice.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import gr.insuranceoffice.dto.OwnersFormDto;
import gr.insuranceoffice.dto.OwnersSubmissionDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.BusinessException.Violation;
import gr.insuranceoffice.service.OwnershipService;
import gr.insuranceoffice.service.SearchService;

/**
 * The owners of one vehicle, edited together (Task 11c). One form with
 * several buttons: adding a customer found by search, removing a row and
 * searching all send the unsaved rows back and forth, and only
 * "Αποθήκευση" writes them, through OwnershipService. No JavaScript needed.
 */
@Controller
public class OwnershipController {

	private final OwnershipService ownershipService;

	private final SearchService searchService;

	public OwnershipController(OwnershipService ownershipService, SearchService searchService) {
		this.ownershipService = ownershipService;
		this.searchService = searchService;
	}

	@GetMapping("/vehicles/{id}/owners")
	public String form(@PathVariable Long id, Model model) {
		return view(ownershipService.ownersForm(id), model);
	}

	// Read as raw parameters: bound to a List<String>, a single share typed
	// as "33,33" would be split on its comma.
	@PostMapping("/vehicles/{id}/owners")
	public String submit(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params, Model model) {
		OwnersSubmissionDto submission = submission(params);
		String action = params.getFirst("action");
		Long add = number(params.getFirst("add"));
		Long remove = number(params.getFirst("remove"));

		if (add != null) {
			submission = withAdded(submission, add);
		} else if (remove != null) {
			submission = withRemoved(submission, remove);
		} else if ("save".equals(action)) {
			try {
				ownershipService.saveOwners(id, submission);
				return "redirect:/vehicles/" + id;
			} catch (BusinessException exception) {
				errors(exception, model);
			} catch (ObjectOptimisticLockingFailureException exception) {
				// SPEC §9: the other change is not overwritten silently.
				model.addAttribute("conflict", "Οι ιδιοκτήτες ή το όχημα άλλαξαν από άλλον χρήστη ενώ τους "
						+ "επεξεργαζόσασταν. Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές και επαναλάβετε "
						+ "τη δική σας.");
			}
		} else if ("search".equals(action)) {
			String query = params.getFirst("q");
			model.addAttribute("q", query);
			model.addAttribute("results", candidates(query, submission));
		}
		return view(ownershipService.ownersForm(id, submission), model);
	}

	private String view(OwnersFormDto owners, Model model) {
		model.addAttribute("owners", owners);
		return "ownership-form";
	}

	// Customers the search found, without those already in the form.
	private List<CustomerHit> candidates(String query, OwnersSubmissionDto submission) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		Set<Long> present = Set.copyOf(submission.customerIds());
		return searchService.search(query).customers().stream()
				.filter(hit -> !present.contains(hit.id()))
				.toList();
	}

	private static void errors(BusinessException exception, Model model) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getViolations().stream()
				.filter(violation -> violation.field() != null)
				.forEach(violation -> errors.putIfAbsent(violation.field(), violation.message()));
		model.addAttribute("errors", errors);
		model.addAttribute("problems", exception.getViolations().stream()
				.filter(violation -> violation.field() == null)
				.map(Violation::message)
				.distinct()
				.collect(Collectors.toList()));
	}

	// The first owner of a vehicle owns all of it until told otherwise.
	private static OwnersSubmissionDto withAdded(OwnersSubmissionDto submission, Long customerId) {
		if (submission.customerIds().contains(customerId)) {
			return submission;
		}
		boolean first = submission.customerIds().isEmpty();
		List<Long> ids = new ArrayList<>(submission.customerIds());
		List<String> percentages = new ArrayList<>(submission.percentages());
		ids.add(customerId);
		percentages.add(first ? "100" : "");
		return new OwnersSubmissionDto(submission.vehicleVersion(), submission.transferDate(),
				first ? customerId : submission.primaryCustomerId(), ids, percentages);
	}

	private static OwnersSubmissionDto withRemoved(OwnersSubmissionDto submission, Long customerId) {
		List<Long> ids = new ArrayList<>(submission.customerIds());
		List<String> percentages = new ArrayList<>(submission.percentages());
		int index = ids.indexOf(customerId);
		if (index >= 0) {
			ids.remove(index);
			percentages.remove(index);
		}
		Long primary = customerId.equals(submission.primaryCustomerId()) ? null : submission.primaryCustomerId();
		return new OwnersSubmissionDto(submission.vehicleVersion(), submission.transferDate(), primary, ids,
				percentages);
	}

	// The rows are two parallel lists in form order. A row whose customer id
	// cannot be read did not come from this form and is dropped.
	private static OwnersSubmissionDto submission(MultiValueMap<String, String> params) {
		List<String> rawIds = params.getOrDefault("customerId", List.of());
		List<String> rawPercentages = params.getOrDefault("percentage", List.of());
		List<Long> ids = new ArrayList<>();
		List<String> percentages = new ArrayList<>();
		for (int i = 0; i < rawIds.size(); i++) {
			Long id = number(rawIds.get(i));
			if (id != null) {
				ids.add(id);
				percentages.add(i < rawPercentages.size() ? rawPercentages.get(i) : "");
			}
		}
		return new OwnersSubmissionDto(number(params.getFirst("vehicleVersion")), date(params.getFirst("transferDate")),
				number(params.getFirst("primary")), ids, percentages);
	}

	private static Long number(String value) {
		try {
			return value == null || value.isBlank() ? null : Long.valueOf(value.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// A date input sends yyyy-MM-dd; anything else counts as missing, and the
	// service asks for it.
	private static LocalDate date(String value) {
		try {
			return value == null || value.isBlank() ? null : LocalDate.parse(value.strip());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

}
