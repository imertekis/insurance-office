package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Task 18: the one place a unique index the database refused becomes a
 * message beside a form field. What the controllers do with it is in
 * UniqueViolationFormTest.
 */
class FormErrorsTest {

	@Test
	void showsTheIndexBesideItsField() {
		Model model = new ExtendedModelMap();

		FormErrors.show(refusal("23505", "idx_vehicle_plate"), model);

		assertThat(model.getAttribute("errors")).isEqualTo(Map.of("plate",
				"Υπάρχει ήδη όχημα με αυτή την πινακίδα· τα ελληνικά και τα λατινικά γράμματα μετρούν ως ίδια."));
		assertThat(model.getAttribute("problems")).isEqualTo(List.of());
	}

	// Not a clerk's mistake but a bug, so it keeps going to the error page.
	@Test
	void rethrowsWhatNoFormHasAFieldFor() {
		for (DataIntegrityViolationException exception : List.of(
				// A foreign key.
				refusal("23503", "ownership_customer_id_fkey"),
				// Accounts are out of scope.
				refusal("23505", "idx_app_user_username"),
				// Only the Excel import creates intermediaries.
				refusal("23505", "idx_intermediary_full_name"))) {
			assertThatThrownBy(() -> FormErrors.show(exception, new ExtendedModelMap())).isSameAs(exception);
		}
	}

	private static DataIntegrityViolationException refusal(String sqlState, String constraint) {
		return new DataIntegrityViolationException("could not execute statement", new PSQLException(
				new ServerErrorMessage("SERROR\0C" + sqlState + "\0Mrefused\0n" + constraint + "\0")));
	}

}
