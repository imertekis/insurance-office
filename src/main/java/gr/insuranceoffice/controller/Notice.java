package gr.insuranceoffice.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Task 16e: what the last action did, said on the page the redirect lands on
 * (templates/fragments/flash.html). A flash attribute, so a reload of that
 * page does not say it again.
 */
final class Notice {

	static final String SAVED = "Αποθηκεύτηκε.";

	static final String RENEWED = "Το συμβόλαιο ανανεώθηκε.";

	private Notice() {
	}

	static void show(RedirectAttributes redirect, String message) {
		redirect.addFlashAttribute("notice", message);
	}

}
