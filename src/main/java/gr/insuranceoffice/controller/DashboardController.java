package gr.insuranceoffice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import gr.insuranceoffice.dto.ExpiryPeriod;
import gr.insuranceoffice.service.DashboardService;

/** The home screen: policies to renew (SPEC §7.1). */
@Controller
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/")
	public String dashboard(@RequestParam(required = false) String period,
			@RequestParam(required = false) String insuranceCompany, Model model) {
		model.addAttribute("dashboard", dashboardService.expiries(ExpiryPeriod.fromParam(period), insuranceCompany));
		model.addAttribute("periods", ExpiryPeriod.values());
		return "dashboard";
	}

}
