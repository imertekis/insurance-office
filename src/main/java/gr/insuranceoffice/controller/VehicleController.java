package gr.insuranceoffice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import gr.insuranceoffice.service.VehicleService;

/** The vehicle card (SPEC §7.2). */
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

}
