package gr.insuranceoffice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** The four states of SPEC §7.3, and where one ends and the next begins. */
class PolicyStatusTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

	@Test
	void aPolicyStartingAfterTodayIsFuture() {
		assertThat(PolicyStatus.of(TODAY.plusDays(1), TODAY.plusYears(1), TODAY)).isEqualTo(PolicyStatus.FUTURE);
		// Even when it will be short or end soon after it starts.
		assertThat(PolicyStatus.of(TODAY.plusDays(1), TODAY.plusDays(5), TODAY)).isEqualTo(PolicyStatus.FUTURE);
	}

	// «Τρέχον» includes the start day: a renewal starting today is in force.
	@Test
	void aPolicyStartingTodayIsInForce() {
		assertThat(PolicyStatus.of(TODAY, TODAY.plusYears(1), TODAY)).isEqualTo(PolicyStatus.ACTIVE);
		assertThat(PolicyStatus.of(TODAY, TODAY.plusDays(10), TODAY)).isEqualTo(PolicyStatus.EXPIRING);
	}

	@Test
	void aPolicyInForceIsActiveUntilThirtyDaysBeforeItsEnd() {
		assertThat(PolicyStatus.of(TODAY.minusMonths(6), TODAY.plusDays(31), TODAY)).isEqualTo(PolicyStatus.ACTIVE);
		assertThat(PolicyStatus.of(TODAY.minusMonths(6), TODAY.plusDays(30), TODAY)).isEqualTo(PolicyStatus.EXPIRING);
	}

	// «Τρέχον» includes the end day too: ending today is not expired yet.
	@Test
	void aPolicyEndingTodayIsStillInForce() {
		assertThat(PolicyStatus.of(TODAY.minusYears(1), TODAY, TODAY)).isEqualTo(PolicyStatus.EXPIRING);
		assertThat(PolicyStatus.of(TODAY.minusYears(1), TODAY.minusDays(1), TODAY)).isEqualTo(PolicyStatus.EXPIRED);
	}

	@Test
	void onlyActiveAndExpiringAreInForce() {
		assertThat(PolicyStatus.ACTIVE.isInForce()).isTrue();
		assertThat(PolicyStatus.EXPIRING.isInForce()).isTrue();
		assertThat(PolicyStatus.FUTURE.isInForce()).isFalse();
		assertThat(PolicyStatus.EXPIRED.isInForce()).isFalse();
	}

	@Test
	void labelsAreGreek() {
		assertThat(PolicyStatus.FUTURE.getLabel()).isEqualTo("Μελλοντικό");
		assertThat(PolicyStatus.ACTIVE.getLabel()).isEqualTo("Ενεργό");
		assertThat(PolicyStatus.EXPIRING.getLabel()).isEqualTo("Λήγει σύντομα");
		assertThat(PolicyStatus.EXPIRED.getLabel()).isEqualTo("Ληγμένο");
	}

}
