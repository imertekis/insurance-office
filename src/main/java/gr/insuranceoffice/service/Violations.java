package gr.insuranceoffice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import gr.insuranceoffice.service.BusinessException.Violation;

/**
 * Collects everything wrong with one save, so a form can show it all at once
 * instead of one problem per attempt. Used by the services; the messages are
 * theirs, in Greek, and the field names are the DTO's.
 */
class Violations {

	private final List<Violation> violations = new ArrayList<>();

	void add(String field, String message) {
		violations.add(new Violation(field, message));
	}

	void addIf(boolean broken, String field, String message) {
		if (broken) {
			add(field, message);
		}
	}

	/** Blank counts as missing: an empty form field means "no value". */
	void required(String field, Object value, String message) {
		addIf(value == null || (value instanceof String text && text.isBlank()), field, message);
	}

	/** Optional field: checked only when it is filled in. */
	void format(String field, String value, Pattern pattern, String message) {
		addIf(value != null && !pattern.matcher(value).matches(), field, message);
	}

	void throwIfAny() {
		if (!violations.isEmpty()) {
			throw new BusinessException(violations);
		}
	}

}
