package gr.insuranceoffice.dto;

import java.util.Arrays;

/** The filters of the expiry screen (SPEC §7.1), as they appear in the URL. */
public enum ExpiryPeriod {

	DAYS_7("7", "7 ημέρες"),
	DAYS_30("30", "30 ημέρες"),
	DAYS_60("60", "60 ημέρες"),
	DAYS_90("90", "90 ημέρες"),
	EXPIRED("expired", "Ληγμένα");

	/** What the screen opens on (CLAUDE.md, resolved conflict 4). */
	public static final ExpiryPeriod DEFAULT = DAYS_30;

	private final String param;
	private final String label;

	ExpiryPeriod(String param, String label) {
		this.param = param;
		this.label = label;
	}

	/** An unknown value, e.g. from an edited URL, falls back to the default. */
	public static ExpiryPeriod fromParam(String param) {
		return Arrays.stream(values()).filter(period -> period.param.equals(param)).findFirst().orElse(DEFAULT);
	}

	public String getParam() {
		return param;
	}

	public String getLabel() {
		return label;
	}

	public boolean isExpired() {
		return this == EXPIRED;
	}

	/** Days ahead for the expiring views; not meaningful for {@link #EXPIRED}. */
	public int getDays() {
		return isExpired() ? 0 : Integer.parseInt(param);
	}

}
