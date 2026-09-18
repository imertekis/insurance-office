package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import gr.insuranceoffice.service.OwnershipService.Share;

/** The rule alone, without a database; the form around it is in OwnershipFormTest. */
class OwnershipServiceTest {

	@Test
	void acceptsASoleOwnerAndAnEvenSplit() {
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("100", true)))).isEmpty();
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("50.00", true), share("50", false)))).isEmpty();
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("33.34", true), share("33.33", false),
				share("33.33", false)))).isEmpty();
	}

	@Test
	void refusesSharesThatDoNotSumToAHundred() {
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("50", true), share("40.5", false))))
				.containsExactly("τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν 90,5% αντί για 100%");
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("60", true), share("60", false))))
				.containsExactly("τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν 120% αντί για 100%");
	}

	@Test
	void requiresExactlyOnePrimaryOwner() {
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("50", false), share("50", false))))
				.containsExactly("το όχημα πρέπει να έχει ακριβώς έναν κύριο ιδιοκτήτη, έχει 0");
		assertThat(OwnershipService.checkCurrentOwners(List.of(share("50", true), share("50", true))))
				.containsExactly("το όχημα πρέπει να έχει ακριβώς έναν κύριο ιδιοκτήτη, έχει 2");
	}

	@Test
	void refusesAVehicleWithoutOwners() {
		assertThat(OwnershipService.checkCurrentOwners(List.of())).hasSize(2);
	}

	private static Share share(String percentage, boolean primary) {
		return new Share(new BigDecimal(percentage), primary);
	}

}
