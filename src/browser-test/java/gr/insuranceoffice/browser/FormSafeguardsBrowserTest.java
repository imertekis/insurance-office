package gr.insuranceoffice.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;

import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Vehicle;

/**
 * Task 16f-2: one submit per form, and a question before leaving a form with
 * changes that were not saved. The question is the browser's own
 * {@code beforeunload} dialog; every dialog is accepted and remembered, so a
 * test sees whether the page asked, and the browser then leaves.
 */
class FormSafeguardsBrowserTest extends BrowserTestBase {

	private final List<String> dialogs = new CopyOnWriteArrayList<>();

	private Customer maria;

	private Customer nikos;

	private Vehicle vehicle;

	@BeforeEach
	void rememberEveryDialog() {
		maria = customer("Αλεξίου", "Μαρία", "900000080", "6900000001");
		nikos = customer("Βασιλείου", "Νίκος", "900000091", "6900000002");
		vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW123456", "Volkswagen", "Golf");
		owns(vehicle, maria, "100", true);
		page.onDialog(dialog -> {
			dialogs.add(dialog.type());
			dialog.accept();
		});
	}

	// On a slow network the page is still there for the second click of a
	// double click, and the first request has already reached the server.
	// The answer is held back until both clicks are done.
	@Test
	void aDoubleClickOnSaveWithASlowAnswerSavesOneCustomer() {
		logInAndOpen("/customers/new");
		page.locator("#lastName").fill("Γεωργίου");
		List<Runnable> answers = new CopyOnWriteArrayList<>();
		page.route("**/customers", route -> {
			if (!"POST".equals(route.request().method())) {
				route.resume();
				return;
			}
			APIResponse answer = route.fetch(new Route.FetchOptions().setMaxRedirects(0));
			answers.add(() -> route.fulfill(new Route.FulfillOptions().setResponse(answer)));
		});

		saveButton().scrollIntoViewIfNeeded();
		BoundingBox save = saveButton().boundingBox();
		double x = save.x + save.width / 2;
		double y = save.y + save.height / 2;
		page.mouse().click(x, y);
		page.waitForCondition(() -> answers.size() == 1);
		page.mouse().click(x, y);
		page.waitForTimeout(500);
		answers.forEach(FormSafeguardsBrowserTest::release);
		page.waitForURL(url -> url.matches(".*/customers/\\d+$"));

		assertThat(customerRepository.findAll()).extracting(Customer::getLastName)
				.containsOnlyOnce("Γεωργίου");
		assertThat(answers).hasSize(1);
	}

	// The owners form lives on the name and value of the button pressed, so
	// the buttons are locked only once the browser has built the request.
	@Test
	void theOwnersFormSendsTheButtonPressed() {
		List<String> sent = sentTo("/vehicles/" + vehicle.getId() + "/owners");
		logInAndOpen("/vehicles/" + vehicle.getId() + "/owners");

		page.locator("#owner-search").fill("900000091");
		clickAndWait(page.locator("#owner-search-button"));
		clickAndWait(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Προσθήκη")));
		assertThat(page.locator("input[name=percentage]")).hasCount(2);
		clickAndWait(page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("Βασιλείου"))
				.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Αφαίρεση")));
		assertThat(page.locator("input[name=percentage]")).hasCount(1);
		clickAndWait(saveButton());
		assertThat(page).hasURL(url("/vehicles/" + vehicle.getId()));

		assertThat(sent).hasSize(4);
		assertThat(sent.get(0)).contains("action=search").doesNotContain("add=", "remove=", "action=save");
		assertThat(sent.get(1)).contains("add=" + nikos.getId()).doesNotContain("action=", "remove=");
		assertThat(sent.get(2)).contains("remove=" + nikos.getId()).doesNotContain("action=", "add=");
		assertThat(sent.get(3)).contains("action=save").doesNotContain("add=", "remove=");
	}

	// REVIEW-08: Enter in the owners' customer search searches, as its button
	// does; Enter in any other field is the hidden «Ανανέωση», which never
	// adds, removes or saves.
	@Test
	void enterSearchesInTheOwnersSearchAndOnlyRefreshesElsewhere() {
		List<String> sent = sentTo("/vehicles/" + vehicle.getId() + "/owners");
		logInAndOpen("/vehicles/" + vehicle.getId() + "/owners");

		page.locator("#owner-search").fill("900000091");
		pressEnterAndWait(page.locator("#owner-search"));
		assertThat(page.locator("#results")).containsText("Βασιλείου Νίκος");
		pressEnterAndWait(page.locator("input[name=percentage]"));
		assertThat(page.locator("input[name=percentage]")).hasCount(1);

		assertThat(sent).hasSize(2);
		assertThat(sent.get(0)).contains("action=search", "q=900000091");
		assertThat(sent.get(1)).contains("action=refresh").doesNotContain("add=", "remove=", "action=save");
	}

	// «Πίσω» to a page the browser kept shows it as it was left, its buttons
	// locked by the submit. The application's pages are sent no-store and
	// Playwright's Chromium keeps no page, so the page is left in that state
	// by cancelling the first send, and the event the browser sends on
	// «Πίσω» is sent here (NOTES, «Manual checks»: «Πίσω» from the cache).
	@Test
	void theButtonsWorkAgainWhenAKeptPageComesBack() {
		logInAndOpen("/customers/new");
		page.locator("#lastName").fill("Γεωργίου");
		AtomicBoolean first = new AtomicBoolean(true);
		page.route("**/customers", route -> {
			if ("POST".equals(route.request().method()) && first.getAndSet(false)) {
				route.abort("aborted");
			}
			else {
				route.resume();
			}
		});
		saveButton().scrollIntoViewIfNeeded();
		BoundingBox save = saveButton().boundingBox();
		page.mouse().click(save.x + save.width / 2, save.y + save.height / 2);
		assertThat(saveButton()).isDisabled();

		page.evaluate("() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))");

		assertThat(saveButton()).isEnabled();
		assertThat(page.locator("form[data-warn-unsaved]")).not().hasAttribute("data-submitting", "");
		clickAndWait(saveButton());
		assertThat(page).hasURL(Pattern.compile(".*/customers/\\d+$"));
		assertThat(customerRepository.findAll()).extracting(Customer::getLastName).containsOnlyOnce("Γεωργίου");
		assertThat(dialogs).isEmpty();
	}

	enum Leaving {
		RELOAD, LINK, BACK, CLOSE
	}

	@ParameterizedTest
	@EnumSource(Leaving.class)
	void leavingAfterTypingAsksFirst(Leaving leaving) {
		logInAndOpen("/");
		page.navigate("/customers/new");
		page.locator("#lastName").pressSequentially("Γεωργίου");

		leave(leaving);

		assertThat(dialogs).containsExactly("beforeunload");
	}

	// Typing a value and typing the old one back leaves nothing to lose.
	@Test
	void leavingAfterTypingTheOldValueBackDoesNotAsk() {
		logInAndOpen("/customers/" + maria.getId() + "/edit");
		page.locator("#lastName").press("End");
		page.locator("#lastName").pressSequentially("υ");
		page.locator("#lastName").press("Backspace");

		leave(Leaving.RELOAD);

		assertThat(dialogs).isEmpty();
	}

	@Test
	void sendingTheFormIsNotLeavingIt() {
		logInAndOpen("/customers/new");
		page.locator("#lastName").pressSequentially("Γεωργίου");

		clickAndWait(saveButton());

		assertThat(page).hasURL(Pattern.compile(".*/customers/\\d+$"));
		assertThat(dialogs).isEmpty();
	}

	// A search box holds nothing to save: the header's, and the owners'
	// customer search, which is inside the owners form.
	@Test
	void typingInASearchBoxDoesNotAsk() {
		logInAndOpen("/vehicles/" + vehicle.getId() + "/owners");
		page.locator("#q").pressSequentially("Αλεξίου");
		page.locator("#owner-search").pressSequentially("Βασιλείου");

		leave(Leaving.LINK);

		assertThat(dialogs).isEmpty();
	}

	// After «Προσθήκη» the rows on the page are not the saved owners, and the
	// server marks the form data-unsaved: leaving asks with nothing typed.
	@Test
	void theOwnersFormAsksFromTheStartWhenItsRowsAreNotSaved() {
		logInAndOpen("/vehicles/" + vehicle.getId() + "/owners");
		leave(Leaving.LINK);
		assertThat(dialogs).isEmpty();

		page.navigate("/vehicles/" + vehicle.getId() + "/owners");
		page.locator("#owner-search").fill("900000091");
		pressEnterAndWait(page.locator("#owner-search"));
		clickAndWait(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Προσθήκη")));
		assertThat(page.locator("form[data-warn-unsaved]")).hasAttribute("data-unsaved", "true");

		leave(Leaving.LINK);

		assertThat(dialogs).containsExactly("beforeunload");
	}

	// Task 23b: a choice changed in a <select> is a change like any other.
	@Test
	void aChangedChoiceAsksToo() {
		logInAndOpen("/vehicles/" + vehicle.getId() + "/edit");
		page.locator("#color").focus();
		page.keyboard().press("ArrowDown");
		assertThat(page.locator("#color")).not().hasValue("Λευκό");

		leave(Leaving.RELOAD);

		assertThat(dialogs).containsExactly("beforeunload");
	}

	private void leave(Leaving leaving) {
		switch (leaving) {
			case RELOAD -> page.reload();
			// The header's link to the home page.
			case LINK -> clickAndWait(page.locator("a.navbar-brand"));
			case BACK -> page.goBack();
			case CLOSE -> page.waitForClose(() -> page.close(new Page.CloseOptions().setRunBeforeUnload(true)));
		}
	}

	// What the page sends to the address, in order: the body of each POST.
	private List<String> sentTo(String path) {
		List<String> bodies = new CopyOnWriteArrayList<>();
		page.onRequest(request -> {
			if ("POST".equals(request.method()) && request.url().endsWith(path)) {
				bodies.add(URLDecoder.decode(request.postData(), StandardCharsets.UTF_8));
			}
		});
		return bodies;
	}

	private void clickAndWait(Locator target) {
		page.waitForLoadState();
		target.click();
		page.waitForLoadState();
	}

	private void pressEnterAndWait(Locator field) {
		field.press("Enter");
		page.waitForLoadState();
	}

	private Locator saveButton() {
		return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Αποθήκευση"));
	}

	// The browser may have given up the first request for the second: then
	// its answer has nowhere to go.
	private static void release(Runnable answer) {
		try {
			answer.run();
		}
		catch (PlaywrightException ignored) {
			// The request was cancelled.
		}
	}

}
