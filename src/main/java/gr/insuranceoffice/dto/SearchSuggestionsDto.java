package gr.insuranceoffice.dto;

import java.util.List;

/**
 * What the header's search box suggests while the clerk types (Task 21a):
 * the first rows of the search page for the same input, at most eight, in
 * its groups and order. Every text is written on the server, in the words of
 * the search page (SPEC §6), so the script that shows them (Task 21b) holds
 * no wording of its own.
 *
 * @param query      the input as typed, so the script can tell which input an
 *                   answer is for
 * @param groups     «Πελάτες», then «Οχήματα», as on the page; a group with
 *                   nothing to suggest is left out
 * @param total      how many rows the search page shows for the same input
 * @param truncated  a group had more matches than the page shows
 * @param totalLabel the total as the link to the page writes it: «12», or
 *                   «50+» when truncated
 */
public record SearchSuggestionsDto(
		String query,
		List<Group> groups,
		int total,
		boolean truncated,
		String totalLabel) {

	/** Nothing to suggest, and nothing searched. */
	public static SearchSuggestionsDto none(String query) {
		return new SearchSuggestionsDto(query, List.of(), 0, false, "0");
	}

	/** «Πελάτες» or «Οχήματα», with its suggestions in the page's order. */
	public record Group(
			String label,
			List<Suggestion> suggestions) {
	}

	/**
	 * One row: «Αλεξίου Κωνσταντίνος» · «ΑΦΜ 189856820 · 2 οχήματα», leading to
	 * {@code /customers/{id}}.
	 *
	 * @param text   the name or the plate
	 * @param detail the second, fainter line
	 * @param url    the card, from the application's root
	 */
	public record Suggestion(
			String text,
			String detail,
			String url) {
	}

}
