package gr.insuranceoffice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gr.insuranceoffice.dto.SearchResultDto;
import gr.insuranceoffice.service.SearchService;

/**
 * The single search box as JSON. Like the Task 1 endpoint it only proves the
 * service end to end; Task 9 shows the results as a page.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping
	public SearchResultDto search(@RequestParam(name = "q", defaultValue = "") String query) {
		return searchService.search(query);
	}

}
