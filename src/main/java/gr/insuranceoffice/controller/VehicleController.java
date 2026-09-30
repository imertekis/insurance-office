package gr.insuranceoffice.controller;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.VehicleService;
import gr.insuranceoffice.service.VehicleValues;

/** The vehicle card (SPEC §7.2) and the form that fills it. */
@Controller
public class VehicleController {

	// «Πολύχρωμο» is a first colour, never a second (Task 23, decision 3).
	private static final List<String> FIRST_COLORS = Stream.concat(VehicleValues.COLORS.stream(),
			Stream.of(VehicleValues.MULTICOLOURED)).toList();

	private final VehicleService vehicleService;

	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	// Task 16d-1: the power (kW), the one decimal of the form, is read with a
	// comma or a point, "12,5" or "12.5": the Greek number keypad of a phone
	// types a comma. Anything else is still refused as unreadable, and
	// FormErrors asks for a number beside the field.
	@InitBinder("vehicle")
	void readDecimalsWithEitherMark(WebDataBinder binder) {
		binder.registerCustomEditor(BigDecimal.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				String typed = text == null ? "" : text.strip();
				setValue(typed.isEmpty() ? null : new BigDecimal(typed.replace(',', '.')));
			}
		});
	}

	// Task 15: by plate, Α-Ω unless the clerk turns it round.
	@GetMapping("/vehicles")
	public String list(@RequestParam(required = false) String dir, @RequestParam(defaultValue = "1") int page,
			Model model) {
		model.addAttribute("list", vehicleService.list(SortDirection.fromParam(dir, SortDirection.ASC), page));
		return "vehicles";
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
	public String create(@ModelAttribute("vehicle") VehicleDto vehicle, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(vehicle, model);
		}
		try {
			VehicleDto saved = vehicleService.create(vehicle);
			Notice.show(redirect, Notice.SAVED);
			return "redirect:/vehicles/" + saved.id();
		} catch (BusinessException exception) {
			return formWithProblems(vehicle, exception, model);
		} catch (DataIntegrityViolationException exception) {
			FormErrors.show(exception, model);
			return form(vehicle, model);
		}
	}

	@PostMapping("/vehicles/{id}")
	public String update(@PathVariable Long id, @ModelAttribute("vehicle") VehicleDto vehicle, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			FormErrors.show(binding, model);
			return form(vehicle, model);
		}
		try {
			vehicleService.update(id, vehicle);
			Notice.show(redirect, Notice.SAVED);
			return "redirect:/vehicles/" + id;
		} catch (BusinessException exception) {
			return formWithProblems(vehicle, exception, model);
		} catch (DataIntegrityViolationException exception) {
			FormErrors.show(exception, model);
			return form(vehicle, model);
		} catch (ObjectOptimisticLockingFailureException exception) {
			// SPEC §9: the other change is not overwritten silently.
			model.addAttribute("conflict", "Το όχημα άλλαξε από άλλον χρήστη ενώ το επεξεργαζόσασταν. "
					+ "Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές του και επαναλάβετε τη δική σας.");
			return form(vehicle, model);
		}
	}

	// Task 11e: ΔΙΑΧΕΙΡΙΣΤΗΣ only; VehicleService refuses anyone else.
	@GetMapping("/vehicles/{id}/delete")
	public String confirmDelete(@PathVariable Long id, Model model) {
		return DeleteConfirmation.show(vehicleService.deletionPreview(id), "/vehicles/" + id + "/delete",
				"/vehicles/" + id, model);
	}

	@PostMapping("/vehicles/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		vehicleService.delete(id);
		Notice.show(redirect, "Το όχημα διαγράφηκε, μαζί με τα συμβόλαια και τις ιδιοκτησίες του. "
				+ "Μπορούν να ανακτηθούν από το ιστορικό αλλαγών.");
		return "redirect:/";
	}

	private String formWithProblems(VehicleDto vehicle, BusinessException exception, Model model) {
		FormErrors.show(exception, model);
		return form(vehicle, model);
	}

	private String form(VehicleDto vehicle, Model model) {
		model.addAttribute("vehicle", vehicle);
		model.addAttribute("usageTypes", UsageType.values());
		model.addAttribute("fuelTypes", FuelType.values());
		// Task 23b: the lists of Task 23a, and the models stored for each
		// brand, all in the page: the form asks the server nothing more.
		model.addAttribute("brands", vehicleService.brandNames());
		model.addAttribute("modelsByBrand", vehicleService.modelsByBrand());
		model.addAttribute("categories", VehicleValues.CATEGORIES);
		model.addAttribute("colors", FIRST_COLORS);
		model.addAttribute("secondColors", VehicleValues.COLORS);
		model.addAttribute("emissionStandards", VehicleValues.EMISSION_STANDARDS);
		return "vehicle-form";
	}

	private static VehicleDto empty() {
		return new VehicleDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null);
	}

}
