package gr.insuranceoffice.controller;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.BusinessException.Violation;

/**
 * Puts what is wrong with a form where the templates look for it: messages
 * about one field in {@code errors}, keyed by the DTO field name, to show
 * beside the input; messages about the record as a whole in {@code problems},
 * to show at the top. The rules themselves are the services'.
 */
final class FormErrors {

	private FormErrors() {
	}

	/** What a service refused. */
	static void show(BusinessException exception, Model model) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getViolations().stream()
				.filter(violation -> violation.field() != null)
				.forEach(violation -> errors.putIfAbsent(violation.field(), violation.message()));
		model.addAttribute("errors", errors);
		model.addAttribute("problems", exception.getViolations().stream()
				.filter(violation -> violation.field() == null)
				.map(Violation::message)
				.distinct()
				.toList());
	}

	/**
	 * What could not even be read, e.g. a word in a number field that the
	 * browser let through. The value never reached the DTO, so the field is
	 * named rather than echoed back.
	 */
	static void show(BindingResult binding, Model model) {
		Map<String, String> errors = new LinkedHashMap<>();
		binding.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), message(error)));
		model.addAttribute("errors", errors);
		// Also said at the top, so the form never comes back without a word
		// when the unreadable field has no place for a message of its own.
		model.addAttribute("problems", List.of("Κάποια πεδία δεν διαβάστηκαν· διορθώστε τα σημειωμένα."));
	}

	private static String message(FieldError error) {
		String codes = String.join(" ", Arrays.asList(error.getCodes() == null ? new String[0] : error.getCodes()));
		if (codes.contains("java.time.LocalDate")) {
			return "Συμπληρώστε ημερομηνία.";
		}
		if (codes.contains("java.lang.Integer") || codes.contains("java.lang.Short")
				|| codes.contains("java.lang.Long") || codes.contains("java.math.BigDecimal")) {
			return "Συμπληρώστε αριθμό.";
		}
		return "Μη έγκυρη τιμή.";
	}

}
