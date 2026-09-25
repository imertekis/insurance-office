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

	// Enter in a field with data-enter-button presses that button, not the
	// form's first one: in the owners form's customer search, Enter searches
	// as «Αναζήτηση» does (after REVIEW-08). Every other field keeps the
	// browser's rule, the form's first button, which there is the hidden
	// «Ανανέωση»: Enter can never add, remove or save. requestSubmit goes
	// through the submit event above, like a click, so a second Enter is
	// cancelled and sending the form is not leaving it.
	document.addEventListener("keydown", function (event) {
		if (event.key !== "Enter" || event.isComposing || event.shiftKey || event.ctrlKey || event.altKey
				|| event.metaKey || event.defaultPrevented) {
			return;
		}
		var input = event.target;
		if (!(input instanceof HTMLInputElement) || !input.form || !input.hasAttribute("data-enter-button")) {
			return;
		}
		var button = document.getElementById(input.getAttribute("data-enter-button"));
		if (!button || button.form !== input.form) {
			return;
		}
		event.preventDefault();
		input.form.requestSubmit(button);
	});

	// Task 20: the owners' shares, filled in where the arithmetic is plain.
	// A help only: the server checks the owners as a whole on saving.
	// - One row, on a page the server marked data-unsaved and with no error
	//   or conflict on it: that row gets 100. That is the page after
	//   «Αφαίρεση» left one owner, or after «Προσθήκη» of the first. Never
	//   what is saved (no data-unsaved), nor what the server refused: the
	//   clerk sees the value beside its error.
	// - Two rows: typing in one sets the other to 100 minus it, when what is
	//   typed is a share (above 0, under 100, two decimals at most).
	// - Three or more: nothing changes; the line under the table gives the
	//   total and what is missing or over, or says a share is not a number.
	// - Shares are read as the server reads them: "50", "50,5", "50.5",
	//   with spaces around; anything else (a word, "1e2") is the server's to
	//   judge. Sums are exact: integers, never floating point, so 100 minus
	//   33,33 is 66,67.
	// This listener is registered before remember() below, so a 100 filled
	// in on load is part of the page as it came, for the leaving warning;
	// such a page is data-unsaved anyway, and leaving it asks.
	var SHARE = /^\s*(\d+)(?:[.,](\d+))?\s*$/;

	// A share as digits and the number of decimals among them, or null.
	function share(text) {
		var match = SHARE.exec(text);
		return match ? { digits: match[1] + (match[2] || ""), decimals: (match[2] || "").length } : null;
	}

	// 5050 hundredths are "50,5"; 7000 are "70".
	function written(value, decimals) {
		var text = value.toString().padStart(decimals + 1, "0");
		var whole = text.slice(0, text.length - decimals);
		var fraction = text.slice(text.length - decimals).replace(/0+$/, "");
		return fraction ? whole + "," + fraction : whole;
	}

	function ownerShares() {
		var total = document.getElementById("owners-total");
		var form = total && total.closest("form");
		return form ? { form: form, total: total, inputs: form.querySelectorAll("input[name=percentage]") } : null;
	}

	function showTotal(owners) {
		if (owners.inputs.length < 3) {
			owners.total.hidden = true;
			owners.total.textContent = "";
			return;
		}
		var read = [];
		var unreadable = 0;
		owners.inputs.forEach(function (input) {
			if (input.value.trim() === "") {
				return;
			}
			var value = share(input.value);
			if (value) {
				read.push(value);
			} else {
				unreadable++;
			}
		});
		var text;
		if (unreadable) {
			text = "Σύνολο: — · " + (unreadable === 1 ? "ένα ποσοστό δεν είναι αριθμός"
				: unreadable + " ποσοστά δεν είναι αριθμοί");
		} else {
			// In the smallest unit typed; an empty share counts as 0.
			var decimals = read.reduce(function (most, value) { return Math.max(most, value.decimals); }, 0);
			var sum = read.reduce(function (sumSoFar, value) {
				return sumSoFar + BigInt(value.digits) * 10n ** BigInt(decimals - value.decimals);
			}, 0n);
			var full = 100n * 10n ** BigInt(decimals);
			text = "Σύνολο: " + written(sum, decimals) + "%";
			if (sum < full) {
				text += " · λείπουν " + written(full - sum, decimals) + "%";
			} else if (sum > full) {
				text += " · περισσεύουν " + written(sum - full, decimals) + "%";
			}
		}
		owners.total.textContent = text;
		owners.total.hidden = false;
	}

	document.addEventListener("DOMContentLoaded", function () {
		var owners = ownerShares();
		if (!owners) {
			return;
		}
		if (owners.inputs.length === 1 && owners.form.getAttribute("data-unsaved") === "true"
				&& !document.querySelector("main .is-invalid, main .alert-danger, main .alert-warning")) {
			owners.inputs[0].value = "100";
		}
		showTotal(owners);
		owners.form.addEventListener("input", function (event) {
			var typed = event.target;
			if (!(typed instanceof HTMLInputElement) || typed.name !== "percentage") {
				return;
			}
			if (owners.inputs.length === 2) {
				var value = share(typed.value);
				var hundredths = value && value.decimals <= 2 ? Number(value.digits) * 10 ** (2 - value.decimals) : 0;
				if (hundredths > 0 && hundredths < 10000) {
					var other = owners.inputs[0] === typed ? owners.inputs[1] : owners.inputs[0];
					other.value = written(10000 - hundredths, 2);
				}
			}
			showTotal(owners);
		});
	});

	// Back to a page the browser kept: the shares are as the clerk left them,
	// and nothing is filled in again; only the total is worked out afresh.
	window.addEventListener("pageshow", function (event) {
		var owners = event.persisted && ownerShares();
		if (owners) {
			showTotal(owners);
		}
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
