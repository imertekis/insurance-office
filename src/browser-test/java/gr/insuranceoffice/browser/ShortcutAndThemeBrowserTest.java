package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ColorScheme;

/**
 * Task 16e: «/» puts the cursor in the header's search box, and printing is
 * black on white. Task 16f-1: the theme button, with its three choices.
 */
class ShortcutAndThemeBrowserTest extends BrowserTestBase {

	private static final List<String> CHOICES = List.of("auto", "light", "dark");

	// From a page with no field in focus: the cursor goes to the search box
	// with its text chosen, so typing replaces it; «/» is not typed, and the
	// list of suggestions does not open, since only typing opens it.
	@Test
	void slashFromThePageChoosesTheTextOfTheSearchBox() {
		logInAndOpen("/search?q=" + URLEncoder.encode("Αλεξίου", StandardCharsets.UTF_8));
		Locator search = page.locator("#q");
		assertThat(search).hasValue("Αλεξίου");
		assertThat(search).not().isFocused();

		page.keyboard().press("/");

		assertThat(search).isFocused();
		assertThat(search).hasValue("Αλεξίου");
		assertThat(search.evaluate("field => [field.selectionStart, field.selectionEnd]")).isEqualTo(List.of(0, 7));
		page.waitForTimeout(700);
		assertThat(page.locator("#search-suggestions")).isHidden();
	}

	@Test
	void slashInAFieldIsTyped() {
		logInAndOpen("/customers/new");

		for (String field : new String[] { "#lastName", "#notes", "#q" }) {
			page.locator(field).click();
			page.keyboard().press("/");
			assertThat(page.locator(field)).isFocused();
			assertThat(page.locator(field)).hasValue("/");
		}
	}

	// «Αυτόματο» follows the OS, here dark, and follows it while the page is
	// open; the button shows the choice, not the theme it gives; the choice
	// stays after a reload.
	@Test
	void theThreeChoicesStayAfterAReload() {
		page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK));
		logInAndOpen("/");
		showsTheme("auto", "dark");

		choose("Φωτεινό");
		showsTheme("light", "light");
		page.reload();
		showsTheme("light", "light");

		choose("Σκούρο");
		showsTheme("dark", "dark");
		page.reload();
		showsTheme("dark", "dark");

		choose("Αυτόματο");
		showsTheme("auto", "dark");
		page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.LIGHT));
		showsTheme("auto", "light");
		page.reload();
		showsTheme("auto", "light");
	}

	// Printing turns the dark theme light, and puts back the clerk's choice
	// after it: «Αυτόματο» stays «Αυτόματο».
	@Test
	void printingIsLightAndPutsTheChoiceBack() {
		page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK));
		logInAndOpen("/");

		choose("Σκούρο");
		assertThat(themeWhilePrinting()).isEqualTo("light");
		showsTheme("dark", "dark");

		choose("Αυτόματο");
		assertThat(themeWhilePrinting()).isEqualTo("light");
		showsTheme("auto", "dark");
		assertThat(page.evaluate("() => localStorage.getItem('theme')")).isEqualTo("auto");
	}

	private void choose(String choice) {
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Θέμα")).click();
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(choice)).click();
	}

	// The choice on <html> and the theme it gives, the button's icon, and
	// the choice marked in the menu.
	private void showsTheme(String chosen, String applied) {
		Locator html = page.locator("html");
		assertThat(html).hasAttribute("data-chosen-theme", chosen);
		assertThat(html).hasAttribute("data-bs-theme", applied);
		for (String choice : CHOICES) {
			Locator icon = page.locator(".theme-switcher-toggle .theme-icon-" + choice);
			Locator button = page.locator("[data-theme-choice=" + choice + "]");
			if (choice.equals(chosen)) {
				assertThat(icon).isVisible();
				assertThat(button).hasAttribute("aria-pressed", "true");
			}
			else {
				assertThat(icon).isHidden();
				assertThat(button).hasAttribute("aria-pressed", "false");
			}
		}
	}

	// Chromium sends beforeprint and afterprint around a PDF, as around a
	// print. The theme is read by a listener added after app.js's own, so it
	// sees what app.js made of the page.
	private Object themeWhilePrinting() {
		page.evaluate("() => { window.themeWhilePrinting = null; window.addEventListener('beforeprint', () => "
				+ "window.themeWhilePrinting = document.documentElement.getAttribute('data-bs-theme'), { once: true }); }");
		page.pdf();
		return page.evaluate("() => window.themeWhilePrinting");
	}

}
