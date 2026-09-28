package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;

import gr.insuranceoffice.entity.Customer;

/**
 * Task 21b: suggestions under the header's search box while the clerk types,
 * from /search/suggestions (Task 21a).
 */
class SearchSuggestionsBrowserTest extends BrowserTestBase {

	private static final Pattern ACTIVE = Pattern.compile("\\bactive\\b");

	// More than the 250 ms app.js waits after the last key, with room for
	// the answer.
	private static final int PAUSE = 700;

	private Customer maria;

	@BeforeEach
	void makeCustomers() {
		// «Αλε» finds both, «Αλεξ» only Maria.
		maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		customer("Αλεβίζος", "Νίκος", "900000091", "6900000002");
	}

	@Test
	void asksFromTheThirdCharacterForJson() {
		List<Request> asked = suggestionsAsked();
		logInAndOpen("/");

		field().pressSequentially("Αλ");
		page.waitForTimeout(PAUSE);
		assertThat(asked).isEmpty();
		assertThat(list()).isHidden();

		field().pressSequentially("ε");
		assertThat(options()).hasText(new String[] { "Αλεβίζος Νίκος ΑΦΜ 900000091 · 0 οχήματα",
				"Αλεξίου Μαρία ΑΦΜ 900000080 · 0 οχήματα", "Όλα τα αποτελέσματα (2) →" });
		assertThat(field()).hasAttribute("aria-expanded", "true");
		assertThat(asked).hasSize(1);
		assertThat(asked.getFirst().allHeaders()).containsEntry("accept", "application/json");

		field().press("Backspace");
		assertThat(list()).isHidden();
		assertThat(field()).hasAttribute("aria-expanded", "false");
	}

	// Down and Up go through the rows and back to none; Esc closes the list
	// and keeps the text; Enter opens the row chosen, or without one searches.
	@Test
	void theKeysMoveThroughTheRowsAndOpenOne() {
		logInAndOpen("/");
		field().pressSequentially("Αλε");
		assertThat(options()).hasCount(3);

		field().press("ArrowDown");
		chosen(0);
		field().press("ArrowDown");
		chosen(1);
		field().press("ArrowDown");
		chosen(2);
		field().press("ArrowDown");
		chosen(-1);
		field().press("ArrowUp");
		chosen(2);
		field().press("ArrowUp");
		chosen(1);

		field().press("Escape");
		assertThat(list()).isHidden();
		assertThat(field()).hasValue("Αλε");
		assertThat(field()).not().hasAttribute("aria-activedescendant", Pattern.compile(".*"));

		field().pressSequentially("ξ");
		assertThat(options()).hasCount(2);
		field().press("ArrowDown");
		field().press("Enter");
		assertThat(page).hasURL(url("/customers/" + maria.getId()));

		field().pressSequentially("Αλεξ");
		assertThat(options()).hasCount(2);
		field().press("Enter");
		assertThat(page).hasURL(Pattern.compile(".*/search\\?q=.*"));
		assertThat(page.locator("main")).containsText("Αλεξίου");
	}

	@Test
	void aClickOnARowOpensItAndAClickElsewhereCloses() {
		logInAndOpen("/");
		field().pressSequentially("Αλε");
		assertThat(options()).hasCount(3);

		page.mouse().click(5, page.viewportSize().height - 5);
		assertThat(list()).isHidden();

		field().click();
		field().press("End");
		field().pressSequentially("ξ");
		assertThat(options()).hasCount(2);
		options().first().click();
		assertThat(page).hasURL(url("/customers/" + maria.getId()));
	}

	// The first answer is held back until the second is shown.
	@Test
	void anAnswerOvertakenByTypingIsNotShown() {
		List<Route> held = holdSuggestions();
		logInAndOpen("/");

		field().pressSequentially("Αλε");
		page.waitForCondition(() -> held.size() == 1);
		field().pressSequentially("ξ");
		page.waitForCondition(() -> held.size() == 2);
		held.get(1).resume();
		assertThat(options()).hasCount(2);
		held.get(0).resume();
		page.waitForTimeout(PAUSE);

		assertThat(options()).hasCount(2);
		assertThat(list()).not().containsText("Αλεβίζος");
	}

	// «Αλε», «Αλεξ», and «Αλε» again: the answer to the first «Αλε» is for
	// the text now in the field, but it was asked before the text changed.
	// Typing cancels it, so the list waits for the latest answer.
	@Test
	void anAnswerToTheSameTextAskedBeforeIsNotShown() {
		List<Route> held = holdSuggestions();
		logInAndOpen("/");

		field().pressSequentially("Αλε");
		page.waitForCondition(() -> held.size() == 1);
		field().pressSequentially("ξ");
		page.waitForCondition(() -> held.size() == 2);
		field().press("Backspace");
		page.waitForCondition(() -> held.size() == 3);

		held.get(0).resume();
		page.waitForTimeout(PAUSE);
		assertThat(list()).isHidden();

		held.get(2).resume();
		assertThat(options()).hasCount(3);
		held.get(1).resume();
		page.waitForTimeout(PAUSE);
		assertThat(options()).hasCount(3);
	}

	// Leaving the field closes the list, and an answer that comes after
	// does not open it again.
	@Test
	void anAnswerAfterLeavingTheFieldIsNotShown() {
		List<Route> held = holdSuggestions();
		logInAndOpen("/");

		field().pressSequentially("Αλε");
		page.waitForCondition(() -> held.size() == 1);
		page.mouse().click(5, page.viewportSize().height - 5);
		held.getFirst().resume();
		page.waitForTimeout(PAUSE);

		assertThat(list()).isHidden();
		assertThat(field()).hasAttribute("aria-expanded", "false");
	}

	// A name is what a clerk typed: shown as text, never as markup.
	@Test
	void aNameIsShownAsText() {
		customer("<b>Τολμηρός</b>", "<i>Γιάννης</i>", "900000102", null);
		logInAndOpen("/");

		field().pressSequentially("τολμ");

		assertThat(options().first().locator(".search-suggestion-text"))
				.hasText("<b>Τολμηρός</b> <i>Γιάννης</i>");
		assertThat(list().locator("b, i")).hasCount(0);
	}

	// Logged out in another tab: no list, and the address of the suggestions
	// is not remembered for after the login (NOTES, Task 21a), so logging in
	// again opens the home page, not their JSON.
	@Test
	void afterALogoutElsewhereLoggingInAgainDoesNotOpenTheSuggestions() {
		logInAndOpen("/");
		Page other = context.newPage();
		logOut(other);

		page.waitForResponse(response -> response.url().contains("/search/suggestions"),
				() -> field().pressSequentially("Αλεξ"));
		page.waitForTimeout(PAUSE);
		assertThat(list()).isHidden();

		logIn(other);
		assertThat(other).hasURL(url("/"));
	}

	// Enter still searches: the login page, then back on the search.
	@Test
	void afterALogoutElsewhereEnterSearchesAfterTheLogin() {
		logInAndOpen("/");
		logOut(context.newPage());

		field().pressSequentially("Αλεξ");
		page.waitForTimeout(PAUSE);
		assertThat(list()).isHidden();
		field().press("Enter");
		assertThat(page).hasURL(Pattern.compile(".*/login$"));
		logIn(page);

		assertThat(page).hasURL(Pattern.compile(".*/search\\?q=.*"));
		assertThat(page.locator("main")).containsText("Αλεξίου");
	}

	// Below 576px each row is two lines, the name and then its second part;
	// the list is as wide as the field, and the page does not scroll sideways
	// (CLAUDE.md, resolved conflict 9). From 576px up, one line.
	@Test
	void onAPhoneEachRowTakesTwoLines() {
		usePhone(375, 812);
		logInAndOpen("/");
		field().pressSequentially("Αλε");
		assertThat(options()).hasCount(3);

		assertThat(secondPartUnderName(options().first())).isTrue();
		BoundingBox fieldBox = field().boundingBox();
		BoundingBox listBox = list().boundingBox();
		assertThat(listBox.x).isCloseTo(fieldBox.x, within(1.0));
		assertThat(listBox.width).isCloseTo(fieldBox.width, within(1.0));
		assertThat((Integer) page.evaluate("() => document.documentElement.scrollWidth")).isLessThanOrEqualTo(375);

		page.setViewportSize(1400, 900);
		assertThat(secondPartUnderName(options().first())).isFalse();
	}

	// A phone with its on-screen keyboard up (375×400, and shorter): the list
	// takes what is in view under the field, less the 8px app.js keeps free,
	// scrolls inside itself down to «Όλα τα αποτελέσματα», and a tap on a
	// row opens it. Without the height app.js measures, the stylesheet's own
	// limit, 70% of the window, would end the list elsewhere.
	@ParameterizedTest
	@ValueSource(ints = { 400, 320 })
	void onAShortScreenTheListScrollsInsideItself(int height) {
		for (int i = 1; i <= 4; i++) {
			customer("Δημητρίου", "Πελάτης " + i, null, "691230000" + i);
			vehicle("ΗΚΑ123" + i, "WVWZZZ1KZAW10000" + i, "Volkswagen", "Golf");
		}
		usePhone(375, height);
		logInAndOpen("/");

		field().pressSequentially("123");
		assertThat(options()).hasCount(9);

		BoundingBox listBox = list().boundingBox();
		assertThat(listBox.y + listBox.height).isCloseTo(height - 8, within(1.0));
		assertThat((Boolean) list().evaluate("list => list.scrollHeight > list.clientHeight")).isTrue();
		field().press("ArrowUp");
		Locator all = options().last();
		assertThat(all).hasClass(ACTIVE);
		BoundingBox allBox = all.boundingBox();
		assertThat(allBox.y).isGreaterThanOrEqualTo(listBox.y);
		assertThat(allBox.y + allBox.height).isLessThanOrEqualTo(listBox.y + listBox.height + 0.5);

		options().first().tap();
		assertThat(page).hasURL(Pattern.compile(".*/customers/\\d+$"));
	}

	private Locator field() {
		return page.locator("#q");
	}

	private Locator list() {
		return page.locator("#search-suggestions");
	}

	private Locator options() {
		return list().getByRole(AriaRole.OPTION);
	}

	// The row the arrows chose, or none (-1): marked for the eye and, through
	// the field, for a screen reader.
	private void chosen(int index) {
		for (int i = 0; i < options().count(); i++) {
			if (i == index) {
				assertThat(options().nth(i)).hasClass(ACTIVE);
				assertThat(options().nth(i)).hasAttribute("aria-selected", "true");
				assertThat(field()).hasAttribute("aria-activedescendant", options().nth(i).getAttribute("id"));
			}
			else {
				assertThat(options().nth(i)).not().hasClass(ACTIVE);
			}
		}
		if (index < 0) {
			assertThat(field()).not().hasAttribute("aria-activedescendant", Pattern.compile(".*"));
		}
	}

	private static boolean secondPartUnderName(Locator row) {
		BoundingBox name = row.locator(".search-suggestion-text").boundingBox();
		BoundingBox detail = row.locator(".search-suggestion-detail").boundingBox();
		return detail.y >= name.y + name.height - 1;
	}

	private List<Request> suggestionsAsked() {
		List<Request> asked = new CopyOnWriteArrayList<>();
		page.onRequest(request -> {
			if (request.url().contains("/search/suggestions")) {
				asked.add(request);
			}
		});
		return asked;
	}

	// Every request for suggestions waits, not yet sent, until the test lets
	// it go.
	private List<Route> holdSuggestions() {
		List<Route> held = new CopyOnWriteArrayList<>();
		page.route("**/search/suggestions?**", held::add);
		return held;
	}

	private static void logOut(Page tab) {
		tab.navigate("/");
		tab.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(FULL_NAME)).click();
		tab.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Αποσύνδεση")).click();
		tab.waitForURL(url -> url.endsWith("/login?logout"));
	}

}
