package gr.insuranceoffice.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import gr.insuranceoffice.dto.SearchSuggestionsDto;
import gr.insuranceoffice.service.SearchService;

/** The single search box (SPEC §6): one input, results grouped by kind. */
@Controller
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping("/search")
	public String search(@RequestParam(name = "q", defaultValue = "") String query, Model model) {
		model.addAttribute("result", searchService.search(query));
		return "search-results";
	}

	/**
	 * The suggestions under the header's box while the clerk types (Task 21a),
	 * for the page's own script: the one answer in JSON (CLAUDE.md, resolved
	 * conflict 2). It needs a login like every page, so without one it is the
	 * same redirect to the login page, not JSON. Never stored by the browser:
	 * the next save changes it.
	 */
	@GetMapping(path = "/search/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SearchSuggestionsDto> suggestions(@RequestParam(name = "q", defaultValue = "") String query) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(searchService.suggest(query));
	}

}
