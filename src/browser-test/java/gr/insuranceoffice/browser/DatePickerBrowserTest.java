package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;

/**
 * Task 16a: a date typed as 03/04/2020 is the 3rd of April, saved and shown
 * so, whatever the browser's own language. The picker (date-picker.js,
 * flatpickr) shows dd/MM/yyyy in a field of its own and sends the ISO date
 * in the field the form names.
 */
class DatePickerBrowserTest extends BrowserTestBase {

	private static final LocalDate THIRD_OF_APRIL = LocalDate.of(2020, 4, 3);

	// An American browser, which reads 03/04/2020 as the 4th of March, in the
	// office's time zone, three hours ahead of UTC: a date turned into UTC
	// on the way would lose a day.
	@Override
	protected Browser.NewContextOptions contextOptions() {
		return new Browser.NewContextOptions().setLocale("en-US").setTimezoneId("Europe/Athens");
	}

	@Test
	void aNewVehicleKeepsTheDateAsTyped() {
		logInAndOpen("/vehicles/new");
		page.locator("#plate").fill("ΑΒΕ1234");
		page.locator("#vin").fill("WVWZZZ1KZAW123456");
		page.locator("#brand").fill("Volkswagen");
		page.locator("#model").fill("Golf");
		typeDate("firstRegistration", "03/04/2020");
		page.locator("#category").selectOption("M1");
		page.locator("#usageType").selectOption("ΕΙΧ");
		page.locator("#color").selectOption("Λευκό");
		page.locator("#fuelType").selectOption("ΒΕΝΖΙΝΗ");
		page.locator("#engineCc").fill("1598");
		page.locator("#powerKw").fill("81");
		save();

		Vehicle saved = vehicleRepository.findAll().getFirst();
		assertThat(saved.getFirstRegistration()).isEqualTo(THIRD_OF_APRIL);
		assertThat(page).hasURL(url("/vehicles/" + saved.getId()));
		assertThat(page.locator("dt:has-text('1η Άδεια') + dd")).hasText("03/04/2020");

		page.navigate("/vehicles/" + saved.getId() + "/edit");
		showsTheThirdOfApril("firstRegistration");
	}

	@Test
	void aNewPolicyKeepsItsDatesAsTyped() {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "Volkswagen", "Golf");

		logInAndOpen("/vehicles/" + vehicle.getId() + "/policies/new");
		page.locator("#policyNumber").fill("2100000001");
		page.locator("#insuranceCompany").fill("Northwind");
		typeDate("startDate", "03/04/2020");
		typeDate("endDate", "03/10/2020");
		page.locator("#premium").fill("180,50");
		save();

		Policy saved = policyRepository.findAll().getFirst();
		assertThat(saved.getStartDate()).isEqualTo(THIRD_OF_APRIL);
		assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2020, 10, 3));
		assertThat(page).hasURL(url("/vehicles/" + vehicle.getId()));
		Locator row = page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("2100000001"));
		assertThat(row.getByRole(AriaRole.CELL).nth(4)).hasText("03/04/2020");
		assertThat(row.getByRole(AriaRole.CELL).nth(5)).hasText("03/10/2020");

		page.navigate("/policies/" + saved.getId() + "/edit");
		showsTheThirdOfApril("startDate");
		assertThat(shown("endDate")).hasValue("03/10/2020");
	}

	// The form sends the ISO date; the field the clerk sees, and the
	// calendar it opens, read 03/04/2020 as the 3rd of April.
	private void showsTheThirdOfApril(String name) {
		assertThat(page.locator("#" + name)).hasValue("2020-04-03");
		assertThat(shown(name)).hasValue("03/04/2020");
		shown(name).click();
		assertThat(page.locator(".flatpickr-calendar.open .flatpickr-day.selected"))
				.hasAttribute("aria-label", "Απρίλιος 3, 2020");
	}

	// Typed, as a clerk does, and left with Tab, where the picker reads it.
	private void typeDate(String name, String date) {
		shown(name).pressSequentially(date);
		shown(name).press("Tab");
	}

	// flatpickr puts the field the clerk sees right after the one the form
	// sends, which it hides.
	private Locator shown(String name) {
		return page.locator("#" + name + " + input");
	}

	private void save() {
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Αποθήκευση")).click();
		page.waitForURL(url -> !url.endsWith("/new"));
	}

}
