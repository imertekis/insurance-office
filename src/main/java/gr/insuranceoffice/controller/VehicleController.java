package gr.insuranceoffice.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.BusinessException.Violation;
import gr.insuranceoffice.service.VehicleService;

/** The vehicle card (SPEC §7.2) and the form that fills it. */
@Controller
public class VehicleController {

	private final VehicleService vehicleService;

	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	@GetMapping("/vehicles/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("detail", vehicleService.findDetail(id));
		return "vehicle-detail";
	}

	@GetMapping("/vehicles/new")
	public String newVehicle(Model model) {
		return form(empty(), model);
	}

	@GetMapping("/vehicles/{id}/edit")
	public String editVehicle(@PathVariable Long id, Model model) {
		return form(vehicleService.find(id), model);
	}

	// The rules live in VehicleService; here they only become messages next to
	// the fields, with what the clerk typed still in the form.
	@PostMapping("/vehicles")
	public String create(@ModelAttribute("vehicle") VehicleDto vehicle, BindingResult binding, Model model) {
		if (binding.hasErrors()) {
			return formWithErrors(vehicle, unreadableFields(binding), List.of(), model);
		}
		try {
			VehicleDto saved = vehicleService.create(vehicle);
			return "redirect:/vehicles/" + saved.id();
		} catch (BusinessException exception) {
			return formWithProblems(vehicle, exception, model);
		}
	}

	@PostMapping("/vehicles/{id}")
	public String update(@PathVariable Long id, @ModelAttribute("vehicle") VehicleDto vehicle, BindingResult binding,
			Model model) {
		if (binding.hasErrors()) {
			return formWithErrors(vehicle, unreadableFields(binding), List.of(), model);
		}
		try {
			vehicleService.update(id, vehicle);
			return "redirect:/vehicles/" + id;
		} catch (BusinessException exception) {
			return formWithProblems(vehicle, exception, model);
		} catch (ObjectOptimisticLockingFailureException exception) {
			// SPEC §9: the other change is not overwritten silently.
			model.addAttribute("conflict", "Το όχημα άλλαξε από άλλον χρήστη ενώ το επεξεργαζόσασταν. "
					+ "Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές του και επαναλάβετε τη δική σας.");
			return form(vehicle, model);
		}
	}

	// A number field the browser let through as text, e.g. "χίλια": the value
	// never reached the DTO, so the field is named rather than echoed back.
	private static Map<String, String> unreadableFields(BindingResult binding) {
		Map<String, String> errors = new LinkedHashMap<>();
		binding.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(),
				numberField(error) ? "Συμπληρώστε αριθμό." : "Μη έγκυρη τιμή."));
		return errors;
	}

	private static boolean numberField(FieldError error) {
		return error.getRejectedValue() instanceof String text && !text.isBlank();
	}

	private String formWithProblems(VehicleDto vehicle, BusinessException exception, Model model) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getViolations().stream()
				.filter(violation -> violation.field() != null)
				.forEach(violation -> errors.putIfAbsent(violation.field(), violation.message()));
		List<String> problems = exception.getViolations().stream()
				.filter(violation -> violation.field() == null)
				.map(Violation::message)
				.toList();
		return formWithErrors(vehicle, errors, problems, model);
	}

	private String formWithErrors(VehicleDto vehicle, Map<String, String> errors, List<String> problems,
			Model model) {
		model.addAttribute("errors", errors);
		model.addAttribute("problems", problems);
		return form(vehicle, model);
	}

	private String form(VehicleDto vehicle, Model model) {
		model.addAttribute("vehicle", vehicle);
		model.addAttribute("usageTypes", UsageType.values());
		model.addAttribute("fuelTypes", FuelType.values());
		return "vehicle-form";
	}

	private static VehicleDto empty() {
		return new VehicleDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null);
	}

}
