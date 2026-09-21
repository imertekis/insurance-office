package gr.insuranceoffice.dto;

import java.util.Arrays;

/** Which way a list page is sorted, as it appears in the URL: {@code ?dir=desc}. */
public enum SortDirection {

	ASC("asc"),
	DESC("desc");

	private final String param;

	SortDirection(String param) {
		this.param = param;
	}

	/** An unknown value, e.g. from an edited URL, falls back to the page's usual order. */
	public static SortDirection fromParam(String param, SortDirection fallback) {
		return Arrays.stream(values()).filter(direction -> direction.param.equals(param)).findFirst()
				.orElse(fallback);
	}

	/** What the sort link in the column heading points to. */
	public SortDirection reversed() {
		return this == ASC ? DESC : ASC;
	}

	public String getParam() {
		return param;
	}

	public boolean isAscending() {
		return this == ASC;
	}

}
