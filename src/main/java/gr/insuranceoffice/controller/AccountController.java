package gr.insuranceoffice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import gr.insuranceoffice.security.CurrentUser;
import gr.insuranceoffice.security.ForcedPasswordChange;
import gr.insuranceoffice.service.AccountService;
import gr.insuranceoffice.service.BusinessException;

/**
 * «Αλλαγή κωδικού» (Task 22a), in the user menu of every page. The page a
 * login with a password that no longer meets the rule is held on until the
 * password is changed (Task 22, decision 1).
 */
@Controller
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping("/account/password")
	public String passwordForm(Model model) {
		model.addAttribute("forced", ForcedPasswordChange.required());
		return "account-password";
	}

	// A refused change comes back with its messages and the three fields
	// empty: a password is never written into a page.
	@PostMapping("/account/password")
	public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
			@RequestParam(defaultValue = "") String newPassword,
			@RequestParam(defaultValue = "") String newPasswordAgain, HttpServletRequest request,
			HttpServletResponse response, Model model, RedirectAttributes redirect) {
		Long userId = CurrentUser.id()
				.orElseThrow(() -> new IllegalStateException("Only an app_user login has a password to change."));
		try {
			accountService.changePassword(userId, currentPassword, newPassword, newPasswordAgain);
		} catch (BusinessException exception) {
			FormErrors.show(exception, model);
			return passwordForm(model);
		}
		ForcedPasswordChange.lift(request, response);
		Notice.show(redirect, "Ο κωδικός άλλαξε.");
		return "redirect:/";
	}

}
