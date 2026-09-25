package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Task 18: which unique index the database refused a save for, read from the
 * index name PostgreSQL sends in its own field ({@code n}), never from the
 * text.
 */
class UniqueConstraintTest {

	@Test
	void readsTheIndexFromItsNameWhateverTheText() {
		// A server set to Greek words the message in Greek.
		assertThat(UniqueConstraint.violatedBy(refusal("23505", "idx_customer_tax_id",
				"η διπλότυπη τιμή κλειδιού παραβιάζει τον περιορισμό μοναδικότητας «idx_customer_tax_id»")))
				.contains(UniqueConstraint.CUSTOMER_TAX_ID);
		// The text names another index; the name field is what counts.
		assertThat(UniqueConstraint.violatedBy(refusal("23505", "idx_policy_number",
				"duplicate key value violates unique constraint \"idx_vehicle_vin\"")))
				.contains(UniqueConstraint.POLICY_NUMBER);
	}

	@Test
	void knowsEveryIndexItLists() {
		for (UniqueConstraint constraint : UniqueConstraint.values()) {
			assertThat(UniqueConstraint.violatedBy(refusal("23505", constraint.indexName(), "")))
					.as(constraint.name()).contains(constraint);
		}
	}

	@Test
	void findsNothingForAnotherViolationOrAnIndexItDoesNotList() {
		// A foreign key (23503) that happens to carry a listed name.
		assertThat(UniqueConstraint.violatedBy(refusal("23503", "idx_customer_tax_id", ""))).isEmpty();
		// Accounts are out of scope (Task 18).
		assertThat(UniqueConstraint.violatedBy(refusal("23505", "idx_app_user_username", ""))).isEmpty();
		assertThat(UniqueConstraint.violatedBy(new DataIntegrityViolationException("no driver exception")))
				.isEmpty();
	}

	/** As Spring and Hibernate hand it on: the driver's exception two causes down. */
	static DataIntegrityViolationException refusal(String sqlState, String constraint, String text) {
		PSQLException postgres = new PSQLException(
				new ServerErrorMessage("SERROR\0C" + sqlState + "\0M" + text + "\0n" + constraint + "\0"));
		return new DataIntegrityViolationException("could not execute statement",
				new ConstraintViolationException("could not execute statement", postgres, "insert", null));
	}

}
