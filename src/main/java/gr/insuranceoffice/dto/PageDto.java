package gr.insuranceoffice.dto;

import java.util.List;

/**
 * One page of a list (Task 15): the rows of the page, where it is and which
 * way it is sorted. Pages are counted from 1, as in the URL.
 *
 * @param items      the rows of this page, at most {@code pageSize}
 * @param page       this page's number, from 1; always a page that exists
 * @param pageSize   how many rows a full page has
 * @param totalItems the rows of the whole list, on every page
 * @param direction  the order of the rows
 */
public record PageDto<T>(
		List<T> items,
		int page,
		int pageSize,
		long totalItems,
		SortDirection direction) {

	/** At least 1, so that an empty list still has a first, empty page. */
	public int getTotalPages() {
		return (int) Math.max(1, (totalItems + pageSize - 1) / pageSize);
	}

	public boolean isFirst() {
		return page == 1;
	}

	public boolean isLast() {
		return page >= getTotalPages();
	}

	/** The page numbers a pager shows around this one: two either side. */
	public int getWindowStart() {
		return Math.max(1, page - 2);
	}

	public int getWindowEnd() {
		return Math.min(getTotalPages(), page + 2);
	}

	/** The number of the first row shown, 1-based; 0 for an empty list. */
	public long getFirstItem() {
		return items.isEmpty() ? 0 : (long) (page - 1) * pageSize + 1;
	}

	public long getLastItem() {
		return items.isEmpty() ? 0 : getFirstItem() + items.size() - 1;
	}

}
