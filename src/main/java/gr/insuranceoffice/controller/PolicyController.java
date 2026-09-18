package gr.insuranceoffice.controller;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import gr.insuranceoffice.dto.PolicyDto;
import gr.insuranceoffice.dto.PolicyFormDto;
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

	@GetMapping("/vehicles/{vehicleId}/policies/new")
	public String newPolicy(@PathVariable Long vehicleId, Model model) {
		return form(policyService.newPolicy(vehicleId), vehicleId, model);
	}

	@GetMapping("/policies/{id}/edit")
	public String editPolicy(@PathVariable Long id, Model model) {
		PolicyFormDto policy = policyService.find(id);
		return form(policy, policy.vehicleId(), model);
	}

	// The rules live in PolicyService; here they only become messages next to
	// the fields, with what the clerk typed still in the form.
	@PostMapping("/vehicles/{vehicleId}/policies")
	public String create(@PathVariable Long vehicleId, @ModelAttribute("policy") PolicyFormDto policy,
			BindingResult binding, Model model) {
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(policy, vehicleId, model);
		}
		try {
			policyService.create(vehicleId, policy);
			return "redirect:/vehicles/" + vehicleId;
		} catch (BusinessException exception) {
			FormErrors.show(exception, model);
			return form(policy, vehicleId, model);
		}
	}

	@PostMapping("/policies/{id}")
	public String update(@PathVariable Long id, @ModelAttribute("policy") PolicyFormDto policy, BindingResult binding,
			Model model) {
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(policy, policy.vehicleId(), model);
		}
		try {
			PolicyDto saved = policyService.update(id, policy);
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
