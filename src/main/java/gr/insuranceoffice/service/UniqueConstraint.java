package gr.insuranceoffice.service;

import java.util.Arrays;
import java.util.Optional;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The unique indexes a save can break, each with the form field it belongs
 * to and the Greek message shown beside it (Task 18). The services' own
 * checks use the same field and message, so the clerk reads one text for one
 * rule, whether the check or the database caught it.
 * <p>
 * A check before the save cannot stop two clerks saving the same value at
 * once: both pass it, and the index refuses the second. The refusal is
 * recognised here by the index's name, which PostgreSQL sends in a field of
 * its own, and never by its message text, which follows the server's
 * language ({@code lc_messages}). Hibernate's
 * {@code ConstraintViolationException.getConstraintName()} reads the name
 * from that text, so it is not used. This is the only class that knows the
 * PostgreSQL driver.
 */
public enum UniqueConstraint {

	CUSTOMER_TAX_ID("idx_customer_tax_id", "taxId", "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ."),

	VEHICLE_VIN("idx_vehicle_vin", "vin", "Υπάρχει ήδη όχημα με αυτό το VIN."),

	// ΑΒΕ1234 and ABE1234 are the same plate (CLAUDE.md §5).
	VEHICLE_PLATE("idx_vehicle_plate", "plate",
			"Υπάρχει ήδη όχημα με αυτή την πινακίδα· τα ελληνικά και τα λατινικά γράμματα μετρούν ως ίδια."),

	POLICY_NUMBER("idx_policy_number", "policyNumber", "Υπάρχει ήδη συμβόλαιο με αυτόν τον αριθμό."),

	// OwnershipService checks the same day itself and names the customer; the
	// index is left to catch two clerks adding the same owner at once.
	OWNERSHIP("idx_ownership_vehicle_customer_from", "transferDate",
			"Οι ιδιοκτήτες του οχήματος άλλαξαν από άλλον χρήστη ενώ τους επεξεργαζόσασταν. "
					+ "Ανοίξτε ξανά την καρτέλα για να δείτε τις αλλαγές και επαναλάβετε τη δική σας."),

	// Intermediaries are only created by the Excel import; no form has the field.
	INTERMEDIARY_NAME("idx_intermediary_full_name", null, null);

	// PostgreSQL's SQLSTATE for unique_violation.
	private static final String UNIQUE_VIOLATION = "23505";

	private final String indexName;

	private final String field;

	private final String message;

	UniqueConstraint(String indexName, String field, String message) {
		this.indexName = indexName;
		this.field = field;
		this.message = message;
	}

	/**
	 * @return the unique index the database refused the save for; empty when
	 *         the exception is another violation (a foreign key, a check) or
	 *         an index not listed here
	 */
	public static Optional<UniqueConstraint> violatedBy(DataIntegrityViolationException exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof PSQLException postgres) {
				ServerErrorMessage server = postgres.getServerErrorMessage();
				if (!UNIQUE_VIOLATION.equals(postgres.getSQLState()) || server == null) {
					return Optional.empty();
				}
				return Arrays.stream(values())
						.filter(constraint -> constraint.indexName.equals(server.getConstraint()))
						.findFirst();
			}
		}
		return Optional.empty();
	}

	public String indexName() {
		return indexName;
	}

	/** The DTO field the message goes beside; null when no form has one. */
	public String field() {
		return field;
	}

	/** In Greek, for the form; null when no form has the field. */
	public String message() {
		return message;
	}

}
