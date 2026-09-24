// Task 16c: the theme choices, and "Αυτόματο" following the OS while the
// page is open. Choosing and applying a theme are in the inline script of
// layout.html (window.appTheme); this file only reacts. Since Task 16f-1 the
// choices are in their own button (fragments/theme-switcher.html), in the
// header and on the login page; its icon follows data-chosen-theme on <html>, which
// appTheme.apply sets, so nothing here draws it.
(function () {
	var theme = window.appTheme;
	if (!theme) {
		return;
	}
	// Kept here as well as in storage, which may be blocked.
	var current = theme.choice();

	function markChoice(chosen) {
		document.querySelectorAll("[data-theme-choice]").forEach(function (button) {
			var current = button.getAttribute("data-theme-choice") === chosen;
			button.classList.toggle("active", current);
			button.setAttribute("aria-pressed", String(current));
		});
	}

	document.querySelectorAll("[data-theme-choice]").forEach(function (button) {
		button.addEventListener("click", function () {
			current = button.getAttribute("data-theme-choice");
			try {
				window.localStorage.setItem("theme", current);
			} catch (e) {
				// Storage blocked: the choice lasts for this page only.
			}
			theme.apply(current);
			markChoice(current);
		});
	});

	theme.osDark.addEventListener("change", function () {
		if (current === "auto") {
			theme.apply("auto");
		}
	});

	markChoice(current);
})();
