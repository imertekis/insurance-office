package gr.insuranceoffice.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A save that breaks a business rule. Carries every problem found, not just
 * the first, so a form can show them all at once.
 */
public class BusinessException extends RuntimeException {

	/**
	 * @param field the DTO field the problem belongs to, or null when it
	 *            concerns the whole record
	 * @param message in Greek, ready to show to the clerk
	 */
	public record Violation(String field, String message) {
	}

	private final List<Violation> violations;

	public BusinessException(List<Violation> violations) {
		super(violations.stream().map(Violation::message).collect(Collectors.joining("; ")));
		this.violations = List.copyOf(violations);
	}

	public List<Violation> getViolations() {
		return violations;
	}

}
