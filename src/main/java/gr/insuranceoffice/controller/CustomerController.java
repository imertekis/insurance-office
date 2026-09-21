package gr.insuranceoffice.controller;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.SavedCustomerDto;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.CustomerService;

/** The customer card (SPEC §7.3) and the form that fills it. */
@Controller
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	// Task 15: by name, Α-Ω unless the clerk turns it round. A page number
	// out of range is put right by the service.
	@GetMapping("/customers")
	public String list(@RequestParam(required = false) String dir, @RequestParam(defaultValue = "1") int page,
			Model model) {
		model.addAttribute("list", customerService.list(SortDirection.fromParam(dir, SortDirection.ASC), page));
		return "customers";
	}

	@GetMapping("/customers/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("detail", customerService.findDetail(id));
		return "customer-detail";
	}

	@GetMapping("/customers/new")
	public String newCustomer(Model model) {
		model.addAttribute("customer", empty());
		return "customer-form";
	}

	@GetMapping("/customers/{id}/edit")
	public String editCustomer(@PathVariable Long id, Model model) {
		model.addAttribute("customer", customerService.find(id));
		return "customer-form";
	}

	// The rules live in CustomerService; here they only become messages next
	// to the fields, with what the clerk typed still in the form.
	@PostMapping("/customers")
	public String create(@ModelAttribute("customer") CustomerDto customer, Model model,
			RedirectAttributes redirect) {
		try {
			return saved(customerService.create(customer), redirect);
		} catch (BusinessException exception) {
			return formWithErrors(customer, exception, model);
		}
	}

	@PostMapping("/customers/{id}")
	public String update(@PathVariable Long id, @ModelAttribute("customer") CustomerDto customer, Model model,
			RedirectAttributes redirect) {
		try {
			return saved(customerService.update(id, customer), redirect);
		} catch (BusinessException exception) {
			return formWithErrors(customer, exception, model);
		} catch (ObjectOptimisticLockingFailureException exception) {
			// SPEC §9: the other change is not overwritten silently.
			model.addAttribute("conflict", "Ο πελάτης άλλαξε από άλλον χρήστη ενώ τον επεξεργαζόσασταν. "
					+ "Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές του και επαναλάβετε τη δική σας.");
			return formWithValues(customer, model);
		}
	}

	// Task 11e: ΔΙΑΧΕΙΡΙΣΤΗΣ only; CustomerService refuses anyone else.
	@GetMapping("/customers/{id}/delete")
	public String confirmDelete(@PathVariable Long id, Model model) {
		return DeleteConfirmation.show(customerService.deletionPreview(id), "/customers/" + id + "/delete",
				"/customers/" + id, model);
	}

	@PostMapping("/customers/{id}/delete")
	public String delete(@PathVariable Long id, Model model, RedirectAttributes redirect) {
		try {
			customerService.delete(id);
		} catch (BusinessException exception) {
			// Became blocked since the page was shown: show why.
			return confirmDelete(id, model);
		}
		redirect.addFlashAttribute("notice", "Ο πελάτης διαγράφηκε. Μπορεί να ανακτηθεί από το ιστορικό αλλαγών.");
		return "redirect:/";
	}

	private String saved(SavedCustomerDto saved, RedirectAttributes redirect) {
		// DECISIONS §2: a missing ΑΦΜ is saved, and said out loud on the card.
		redirect.addFlashAttribute("warnings", saved.warnings());
		return "redirect:/customers/" + saved.customer().id();
	}

	private String formWithErrors(CustomerDto customer, BusinessException exception, Model model) {
		FormErrors.show(exception, model);
		return formWithValues(customer, model);
	}

	private String formWithValues(CustomerDto customer, Model model) {
		model.addAttribute("customer", customer);
		return "customer-form";
	}

	private static CustomerDto empty() {
		return new CustomerDto(null, null, "INDIVIDUAL", null, null, null, null, null, null, null, null, null, null,
				null, null, null, null);
	}

}
