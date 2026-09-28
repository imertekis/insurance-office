package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Vehicle;

/**
 * Task 20: the owners' shares filled in where the arithmetic is plain, and
 * their total under three owners or more, as the clerk types.
 */
class OwnerSharesBrowserTest extends BrowserTestBase {

	private Customer maria;

	private Customer nikos;

	private Customer anna;

	private Vehicle vehicle;

	@BeforeEach
	void makeOwners() {
		maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		anna = customer("Γεωργίου", "Άννα", "123456783", "6900000003");
		vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "Volkswagen", "Golf");
	}

	@Test
	void theOwnerLeftAfterARemovalGetsAHundred() {
		owns(vehicle, maria, "50", true);
		owns(vehicle, nikos, "50", false);
		logInAndOpen(ownersPage());

		remove(nikos);

		assertThat(page.locator("input[name=percentage]")).hasCount(1);
		assertThat(share(maria)).hasValue("100");
	}

	// Never what the server refused: the clerk sees the value beside its error.
	@Test
	void aShareTheServerRefusedStaysAsTyped() {
		owns(vehicle, maria, "100", true);
		logInAndOpen(ownersPage());

		share(maria).fill("50");
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Αποθήκευση")).click();

		assertThat(page.locator("main .alert-danger, main .is-invalid").first()).isVisible();
		assertThat(share(maria)).hasValue("50");
	}

	@Test
	void withTwoOwnersTheOtherGetsTheRest() {
		owns(vehicle, maria, "50", true);
		owns(vehicle, nikos, "50", false);
		logInAndOpen(ownersPage());

		typeShare(maria, "30");
		assertThat(share(nikos)).hasValue("70");
		typeShare(maria, "49,5");
		assertThat(share(nikos)).hasValue("50,5");
		typeShare(maria, "33,33");
		assertThat(share(nikos)).hasValue("66,67");
		typeShare(nikos, "25");
		assertThat(share(maria)).hasValue("75");
		assertThat(page.locator("#owners-total")).isHidden();
	}

	// Nothing that is not a share above 0 and under 100, with two decimals
	// at most, changes the other owner.
	@Test
	void withTwoOwnersWhatIsNoShareChangesNothing() {
		owns(vehicle, maria, "50", true);
		owns(vehicle, nikos, "50", false);
		logInAndOpen(ownersPage());
		typeShare(maria, "20");
		assertThat(share(nikos)).hasValue("80");

		for (String typed : new String[] { "0", "100", "150", "abc", "1e2", "33,333", "" }) {
			share(maria).fill(typed);
			assertThat(share(nikos)).hasValue("80");
		}
	}

	// With three or more nothing is filled in; the line under the table
	// gives the total at every key.
	@Test
	void withThreeOwnersTheTotalFollowsEveryKey() {
		owns(vehicle, maria, "50", true);
		owns(vehicle, nikos, "30", false);
		owns(vehicle, anna, "20", false);
		logInAndOpen(ownersPage());
		Locator total = page.locator("#owners-total");
		assertThat(total).hasText("Σύνολο: 100%");

		share(anna).fill("");
		assertThat(total).hasText("Σύνολο: 80% · λείπουν 20%");
		// «25,» is no number until the decimal after it is typed.
		String[] totals = { "Σύνολο: 82% · λείπουν 18%", "Σύνολο: 105% · περισσεύουν 5%",
				"Σύνολο: — · ένα ποσοστό δεν είναι αριθμός", "Σύνολο: 105,5% · περισσεύουν 5,5%" };
		String typed = "25,5";
		for (int i = 0; i < typed.length(); i++) {
			share(anna).pressSequentially(typed.substring(i, i + 1));
			assertThat(total).hasText(totals[i]);
		}
		assertThat(share(maria)).hasValue("50");
		assertThat(share(nikos)).hasValue("30");

		share(nikos).fill("τριάντα");
		assertThat(total).hasText("Σύνολο: — · ένα ποσοστό δεν είναι αριθμός");
		share(maria).fill("50%");
		assertThat(total).hasText("Σύνολο: — · 2 ποσοστά δεν είναι αριθμοί");
	}

	private String ownersPage() {
		return "/vehicles/" + vehicle.getId() + "/owners";
	}

	private Locator row(Customer customer) {
		return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(customer.getLastName()));
	}

	private Locator share(Customer customer) {
		return row(customer).locator("input[name=percentage]");
	}

	// Cleared, then typed key by key, as a clerk does.
	private void typeShare(Customer customer, String value) {
		share(customer).fill("");
		share(customer).pressSequentially(value);
	}

	private void remove(Customer customer) {
		row(customer).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Αφαίρεση")).click();
		page.waitForLoadState();
	}

}
