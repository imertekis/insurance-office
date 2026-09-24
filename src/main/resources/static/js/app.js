// Task 16e: small helpers for every page, and since Task 16f-2 the
// safeguards of the forms. Loaded by layout :: head with defer, on every
// page, the login page included; each part checks for what it needs.
(function () {
	// "/" anywhere puts the cursor in the header's search box, the main way
	// into the application, without typing the "/" into it.
	// - event.key, not keyCode: the key that gives "/" differs between
	//   layouts (Shift+7 on a German one), and event.key is the character
	//   typed. On the Greek layout it is the same key as on the US one, right
	//   of the full stop, and the keypad's "/" gives it on any layout.
	// - Typed as usual where text goes (input, textarea, select, editable
	//   content), and not with Ctrl/Alt/Meta, which belong to the browser.
	// - No search box (the login page): nothing happens.
	document.addEventListener("keydown", function (event) {
		if (event.key !== "/" || event.ctrlKey || event.altKey || event.metaKey || event.isComposing
				|| event.defaultPrevented) {
			return;
		}
		var target = event.target;
		if (target instanceof Element
				&& (target.closest("input, textarea, select") || target.isContentEditable)) {
			return;
		}
		var search = document.getElementById("q");
		if (!search) {
			return;
		}
		event.preventDefault();
		search.focus();
		search.select();
	});

	// Task 16f-2, part 1: one submit per form. A double click would send a
	// POST twice: two customers, or a conflict message for the second edit.
	// - The first submit goes through untouched. Its buttons are not disabled
	//   before the browser has built the request: a disabled button is left
	//   out of the form's data, and the owners form lives on the name and
	//   value of the button pressed (action=save, remove=<id>, add=<id>). So
	//   they turn disabled a moment later (setTimeout), and until then any
	//   further submit of the same form is cancelled.
	// - GET forms (the search box, the list filters) are left alone: sending
	//   them twice changes nothing.
	// - After "Back" the browser may show the page from its cache as it was
	//   left, buttons disabled: pageshow gives them back.
	var SUBMIT_BUTTONS = "button[type=submit], button:not([type]), input[type=submit]";
	// A form with data-warn-unsaved is being sent: leaving is not losing it.
	var leaving = false;

	document.addEventListener("submit", function (event) {
		var form = event.target;
		if (!(form instanceof HTMLFormElement) || form.method !== "post") {
			return;
		}
		if (form.hasAttribute("data-submitting")) {
			event.preventDefault();
			return;
		}
		if (event.defaultPrevented) {
			return;
		}
		form.setAttribute("data-submitting", "");
		if (form.hasAttribute("data-warn-unsaved")) {
			leaving = true;
		}
		setTimeout(function () {
			form.querySelectorAll(SUBMIT_BUTTONS).forEach(function (button) {
				if (!button.disabled) {
					button.disabled = true;
					button.setAttribute("data-disabled-by-submit", "");
				}
			});
		}, 0);
	});

	// Task 16f-2, part 2: leaving a form with changes that were not saved
	// asks first, in the browser's own words (no text of ours is shown).
	// - Only the forms marked data-warn-unsaved: customer, vehicle, policy,
	//   owners. Not the login, the delete confirmation or the search.
	// - "Changes" are against the form as the page came: its named fields,
	//   without the CSRF token and without a search box, which holds nothing
	//   to save. After a refused save the page comes back with what was
	//   typed, and that is the starting point: leaving it unchanged does not
	//   ask. Typing a value and typing the old one back does not ask either.
	// - The owners form may come with data-unsaved="true" from the server:
	//   its rows are not the saved owners (after «Προσθήκη», «Αφαίρεση», a
	//   refused save). Then leaving asks from the start.
	// - Sending the form, with any of its buttons, is not leaving it.
	var initial = new Map();

	function state(form) {
		var skipped = ["_csrf"];
		form.querySelectorAll("input[type=search][name]").forEach(function (input) {
			skipped.push(input.name);
		});
		var entries = [];
		new FormData(form).forEach(function (value, name) {
			if (skipped.indexOf(name) < 0) {
				entries.push(name + "=" + value);
			}
		});
		return entries.join("\n");
	}

	function remember() {
		initial.clear();
		document.querySelectorAll("form[data-warn-unsaved]").forEach(function (form) {
			initial.set(form, state(form));
		});
	}

	// This script is deferred, so this listener runs after the one of
	// date-picker.js, which the page loads earlier: the date fields are
	// already set up (flatpickr, Task 16a) when the form is remembered. The
	// date the clerk sees has no name; its ISO twin is what counts.
	document.addEventListener("DOMContentLoaded", remember);

	window.addEventListener("beforeunload", function (event) {
		if (leaving) {
			return;
		}
		var unsaved = false;
		initial.forEach(function (start, form) {
			if (form.getAttribute("data-unsaved") === "true" || state(form) !== start) {
				unsaved = true;
			}
		});
		if (unsaved) {
			event.preventDefault();
			event.returnValue = "";
		}
	});

	// Back to a page the browser kept: the buttons work again, and the form
	// as it came back is the new starting point.
	window.addEventListener("pageshow", function (event) {
		if (!event.persisted) {
			return;
		}
		leaving = false;
		document.querySelectorAll("form[data-submitting]").forEach(function (form) {
			form.removeAttribute("data-submitting");
			form.querySelectorAll("[data-disabled-by-submit]").forEach(function (button) {
				button.disabled = false;
				button.removeAttribute("data-disabled-by-submit");
			});
		});
		remember();
	});

	// Print is black on white, from the dark theme too. The theme lives only
	// in data-bs-theme (Task 16c), so the page turns light for the print and
	// back after it, through the one function that applies a theme. What is
	// put back is the clerk's choice (Task 16f-1), not the theme it gave: an
	// "Αυτόματο" must stay "Αυτόματο" after printing.
	var theme = window.appTheme;
	if (theme) {
		var chosen = null;
		window.addEventListener("beforeprint", function () {
			chosen = document.documentElement.getAttribute("data-chosen-theme");
			theme.apply("light");
		});
		window.addEventListener("afterprint", function () {
			if (chosen) {
				theme.apply(chosen);
				chosen = null;
			}
		});
	}
})();
