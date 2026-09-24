// Task 16e: small helpers for every page. Loaded by layout :: head with
// defer, on every page, the login page included; each part checks for what
// it needs.
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

	// Print is black on white, from the dark theme too. The theme lives only
	// in data-bs-theme (Task 16c), so the page turns light for the print and
	// back after it, through the one function that applies a theme.
	var theme = window.appTheme;
	if (theme) {
		var shown = null;
		window.addEventListener("beforeprint", function () {
			shown = document.documentElement.getAttribute("data-bs-theme");
			theme.apply("light");
		});
		window.addEventListener("afterprint", function () {
			if (shown) {
				theme.apply(shown);
				shown = null;
			}
		});
	}
})();
