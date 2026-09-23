// Task 16a: every date field (class "js-date") gets a picker that always
// shows and accepts dd/MM/yyyy, regardless of the browser's own locale.
// flatpickr's altInput keeps the original input (name, submitted value) in
// ISO format for the server; only the input the clerk sees changes format.
document.addEventListener("DOMContentLoaded", function () {
	if (typeof flatpickr === "undefined") {
		return;
	}
	flatpickr.localize(flatpickr.l10ns.gr);
	document.querySelectorAll("input.js-date").forEach(function (input) {
		flatpickr(input, {
			dateFormat: "Y-m-d",
			altInput: true,
			altFormat: "d/m/Y",
			allowInput: true,
		});
	});
});
