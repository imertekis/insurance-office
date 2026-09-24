package gr.insuranceoffice.controller;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import gr.insuranceoffice.dto.DeletionPreviewDto;
import gr.insuranceoffice.dto.PolicyDto;
import gr.insuranceoffice.dto.PolicyFormDto;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.PolicyService;
import gr.insuranceoffice.service.VehicleService;

/** The policy form (Task 11d), opened from the vehicle card. */
@Controller
public class PolicyController {

	private final PolicyService policyService;

	private final VehicleService vehicleService;

	public PolicyController(PolicyService policyService, VehicleService vehicleService) {
		this.policyService = policyService;
		this.vehicleService = vehicleService;
	}

	// Task 15: latest end date first, since the recent policies are the ones
	// looked for; the insurance company filter is the expiry screen's.
	@GetMapping("/policies")
	public String list(@RequestParam(required = false) String insuranceCompany,
			@RequestParam(required = false) String dir, @RequestParam(defaultValue = "1") int page, Model model) {
		model.addAttribute("list",
				policyService.list(insuranceCompany, SortDirection.fromParam(dir, SortDirection.DESC), page));
		return "policies";
	}

	@GetMapping("/vehicles/{vehicleId}/policies/new")
	public String newPolicy(@PathVariable Long vehicleId, Model model) {
		return form(policyService.newPolicy(vehicleId), vehicleId, model);
	}

	@GetMapping("/policies/{id}/edit")
	public String editPolicy(@PathVariable Long id, Model model) {
		PolicyFormDto policy = policyService.find(id);
		return form(policy, policy.vehicleId(), model);
	}

	// Task 12: the everyday action. The Task 11d form, prefilled from the
	// policy being renewed; saving goes through create, so the old policy is
	// never touched and every 11d rule applies.
	@GetMapping("/policies/{id}/renew")
	public String renew(@PathVariable Long id, Model model) {
		PolicyFormDto renewal = policyService.renewal(id);
		model.addAttribute("renewalOf", policyService.find(id).policyNumber());
		return form(renewal, renewal.vehicleId(), model);
	}

	// The rules live in PolicyService; here they only become messages next to
	// the fields, with what the clerk typed still in the form.
	@PostMapping("/vehicles/{vehicleId}/policies")
	public String create(@PathVariable Long vehicleId, @ModelAttribute("policy") PolicyFormDto policy,
			BindingResult binding, @RequestParam(required = false) String renewalOf, Model model,
			RedirectAttributes redirect) {
		// Still a renewal when the form comes back with a mistake.
		model.addAttribute("renewalOf", renewalOf);
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(policy, vehicleId, model);
		}
		try {
			policyService.create(vehicleId, policy);
			// Task 12: a renewal is a new policy, but the clerk renewed one.
			Notice.show(redirect, renewalOf != null ? Notice.RENEWED : Notice.SAVED);
			return "redirect:/vehicles/" + vehicleId;
		} catch (BusinessException exception) {
			FormErrors.show(exception, model);
			return form(policy, vehicleId, model);
		}
	}

	@PostMapping("/policies/{id}")
	public String update(@PathVariable Long id, @ModelAttribute("policy") PolicyFormDto policy, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(policy, policy.vehicleId(), model);
		}
		try {
			PolicyDto saved = policyService.update(id, policy);
			Notice.show(redirect, Notice.SAVED);
			return "redirect:/vehicles/" + saved.vehicleId();
		} catch (BusinessException exception) {
			FormErrors.show(exception, model);
			return form(policy, policy.vehicleId(), model);
		} catch (ObjectOptimisticLockingFailureException exception) {
			// SPEC §9: the other change is not overwritten silently.
			model.addAttribute("conflict", "Το συμβόλαιο άλλαξε από άλλον χρήστη ενώ το επεξεργαζόσασταν. "
					+ "Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές του και επαναλάβετε τη δική σας.");
			return form(policy, policy.vehicleId(), model);
		}
	}

	// Task 11e: ΔΙΑΧΕΙΡΙΣΤΗΣ only; PolicyService refuses anyone else.
	@GetMapping("/policies/{id}/delete")
	public String confirmDelete(@PathVariable Long id, Model model) {
		DeletionPreviewDto preview = policyService.deletionPreview(id);
		return DeleteConfirmation.show(preview, "/policies/" + id + "/delete", "/vehicles/" + preview.vehicleId(),
				model);
	}

	@PostMapping("/policies/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		Long vehicleId = policyService.delete(id);
		Notice.show(redirect, "Το συμβόλαιο διαγράφηκε. Μπορεί να ανακτηθεί από το ιστορικό αλλαγών.");
		return "redirect:/vehicles/" + vehicleId;
	}

	private String form(PolicyFormDto policy, Long vehicleId, Model model) {
		model.addAttribute("policy", policy);
		model.addAttribute("vehicleId", vehicleId);
		model.addAttribute("plate", vehicleService.find(vehicleId).plate());
		// Task 11d: an intermediary is picked from the existing ones only.
		model.addAttribute("intermediaries", policyService.selectableIntermediaries(policy.intermediaryId()));
		model.addAttribute("insuranceCompanies", policyService.knownInsuranceCompanies());
		model.addAttribute("surchargeTypes", SurchargeType.values());
		return "policy-form";
	}

}
