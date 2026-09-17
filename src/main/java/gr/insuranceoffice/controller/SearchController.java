package gr.insuranceoffice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

}
