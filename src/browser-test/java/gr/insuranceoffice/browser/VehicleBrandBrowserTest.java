package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import gr.insuranceoffice.entity.Vehicle;

/**
 * Task 23b: the vehicle form's brand, searched in the list of the page as it
 * is typed and never left outside it, and the model's suggestions, which
 * follow the brand. The brands are those of V7__vehicle_brand.sql.
 */
class VehicleBrandBrowserTest extends BrowserTestBase {

	private static final Pattern ACTIVE = Pattern.compile("\\bactive\\b");

	// A clerk who did not switch the keyboard to Latin: the keys b, m, w
	// give «βμς», and a, u, d, i give «αθδι». Enter never sends the form
	// while the list is open.
	@Test
	void greekLettersCountAsTheLatinOfTheirKey() {
		logInAndOpen("/vehicles/new");

		brand().pressSequentially("βμς");
		assertThat(shown()).hasText(new String[] { "BMW" });
		brand().press("ArrowDown");
		assertThat(shown().first()).hasClass(ACTIVE);
		assertThat(brand()).hasAttribute("aria-activedescendant", shown().first().getAttribute("id"));
		brand().press("Enter");
		assertThat(brand()).hasValue("BMW");
		assertThat(list()).isHidden();

		brand().fill("");
		brand().pressSequentially("αθδι");
		assertThat(shown()).hasText(new String[] { "Audi" });
		brand().press("Enter");
		assertThat(brand()).hasValue("Audi");
		assertThat(page).hasURL(url("/vehicles/new"));
	}

	// A word of the name counts too; case and accents do not. What is typed
	// in full becomes the brand when the field is left.
	@Test
	void aBrandTypedInFullIsWrittenAsTheList() {
		logInAndOpen("/vehicles/new");

		brand().pressSequentially("benz");
		assertThat(shown()).hasText(new String[] { "Mercedes-Benz" });
		brand().fill("");
		brand().pressSequentially("skoda");
		assertThat(shown()).hasText(new String[] { "Škoda" });
		brand().press("Tab");

		assertThat(brand()).hasValue("Škoda");
		assertThat(list()).isHidden();
	}

	@Test
	void aClickOnABrandTakesIt() {
		logInAndOpen("/vehicles/new");

		brand().pressSequentially("toy");
		shown().filter(new Locator.FilterOptions().setHasText("Toyota")).click();

		assertThat(brand()).hasValue("Toyota");
		assertThat(list()).isHidden();
		assertThat(brand()).isFocused();
	}

	// A click in the field, or Down, opens the whole list with the field's
	// brand chosen; Esc closes it. Typing something that is no brand and
	// leaving puts the last brand back; emptied, the field stays empty.
	@Test
	void onlyABrandOfTheListStays() {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "Volkswagen", "Golf");
		logInAndOpen("/vehicles/" + vehicle.getId() + "/edit");

		brand().click();
		assertThat(shown().filter(new Locator.FilterOptions().setHasText("Volkswagen"))).hasClass(ACTIVE);
		assertThat(shown().filter(new Locator.FilterOptions().setHasText("Abarth"))).isVisible();
		brand().press("Escape");
		assertThat(list()).isHidden();
		brand().press("ArrowDown");
		assertThat(list()).isVisible();

		brand().fill("ΛΑΔΑΚΙ");
		assertThat(list().getByText("Καμία μάρκα της λίστας")).isVisible();
		brand().press("Tab");
		assertThat(brand()).hasValue("Volkswagen");

		brand().fill("");
		brand().press("Tab");
		assertThat(brand()).hasValue("");
	}

	// Task 23a, decision 7: a brand from before the list is saved again as it
	// is, while it is not typed over.
	@Test
	void aBrandFromBeforeTheListStaysWhileNotTypedOver() {
		Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "VW", "Golf");
		logInAndOpen("/vehicles/" + vehicle.getId() + "/edit");

		brand().click();
		brand().press("Escape");
		brand().press("Tab");
		assertThat(brand()).hasValue("VW");
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Αποθήκευση")).click();

		assertThat(page).hasURL(url("/vehicles/" + vehicle.getId()));
		assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getBrand()).isEqualTo("VW");
	}

	// Task 23, decision 5: the models stored for the brand in the field, from the
	// page; any model can still be typed. The browser draws the list itself,
	// so what is checked is the list the field points at.
	@Test
	void theModelSuggestionsFollowTheBrand() {
		vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "Volkswagen", "Golf");
		vehicle("ΑΒΕ1235", "WVWZZZ1KZAW123457", "Volkswagen", "Polo");
		Vehicle yaris = vehicle("ΑΒΕ1236", "JTDKW923X05123458", "Toyota", "Yaris");
		logInAndOpen("/vehicles/new");
		assertThat(modelSuggestions()).isEmpty();

		brand().pressSequentially("volkswagen");
		brand().press("Tab");
		assertThat(modelSuggestions()).containsExactlyInAnyOrder("Golf", "Polo");
		brand().fill("toy");
		brand().press("Enter");
		assertThat(modelSuggestions()).containsExactly("Yaris");
		brand().fill("βμς");
		brand().press("Enter");
		assertThat(modelSuggestions()).isEmpty();

		page.navigate("/vehicles/" + yaris.getId() + "/edit");
		assertThat(modelSuggestions()).containsExactly("Yaris");
	}

	private Locator brand() {
		return page.locator("#brand");
	}

	private Locator list() {
		return page.locator("#brand-options");
	}

	// The brands the list shows now.
	private Locator shown() {
		return list().locator("[role=option]:not([aria-disabled]):visible");
	}

	@SuppressWarnings("unchecked")
	private List<String> modelSuggestions() {
		return (List<String>) page.locator("#model")
				.evaluate("model => model.list ? Array.from(model.list.options, option => option.value) : []");
	}

}
