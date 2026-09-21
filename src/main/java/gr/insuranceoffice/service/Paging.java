package gr.insuranceoffice.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import gr.insuranceoffice.dto.PageDto;
import gr.insuranceoffice.dto.SortDirection;

/**
 * What the list pages of Task 15 share: a fixed page size, and a page number
 * that is always one that exists. No list is loaded whole (REVIEW-04).
 */
final class Paging {

	/** As many rows as the search shows per group. */
	static final int PAGE_SIZE = 50;

	// The largest page whose offset still fits the int JPA takes for it.
	private static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE - 1;

	private Paging() {
	}

	/**
	 * @param requested      the page number asked for, from 1; anything else,
	 *                       or a page past the end, gives the nearest real one
	 * @param sortProperties entity properties to order by, all in the direction
	 *                       given, so that the reverse order is an exact
	 *                       mirror; end with a unique one so pages never overlap
	 * @param query          runs the paged query
	 * @param toDtos         maps the rows of the page to their DTOs
	 */
	static <E, D> PageDto<D> page(int requested, SortDirection direction, List<String> sortProperties,
			Function<Pageable, Page<E>> query, Function<List<E>, List<D>> toDtos) {
		Sort sort = Sort.by(Sort.Direction.valueOf(direction.name()), sortProperties.toArray(String[]::new));
		int page = Math.min(Math.max(requested, 1), MAX_PAGE);
		Page<E> found = query.apply(PageRequest.of(page - 1, PAGE_SIZE, sort));
		// Past the end, by a typed URL or after rows were deleted: the last page.
		if (found.isEmpty() && found.getTotalElements() > 0) {
			page = found.getTotalPages();
			found = query.apply(PageRequest.of(page - 1, PAGE_SIZE, sort));
		}
		return new PageDto<>(toDtos.apply(found.getContent()), page, PAGE_SIZE, found.getTotalElements(), direction);
	}

}
