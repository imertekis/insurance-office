package gr.insuranceoffice.controller;

import org.springframework.ui.Model;

import gr.insuranceoffice.dto.DeletionPreviewDto;

/**
 * The one page every delete passes through (Task 11e): what will be deleted,
 * what goes with it, and why it cannot go yet if it cannot. The delete itself
 * is a POST from that page.
 */
final class DeleteConfirmation {

	static final String VIEW = "confirm-delete";

	private DeleteConfirmation() {
	}

	static String show(DeletionPreviewDto preview, String action, String cancel, Model model) {
		model.addAttribute("preview", preview);
		model.addAttribute("action", action);
		model.addAttribute("cancel", cancel);
		return VIEW;
	}

}
