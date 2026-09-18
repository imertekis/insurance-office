package gr.insuranceoffice.dto;

import java.time.LocalDate;

/**
 * How a policy stands today, for the coloured badges of SPEC §7.2 and §7.3.
 * «Τρέχον» = CURRENT_DATE BETWEEN start_date AND end_date (DATA_MODEL): an
 * ACTIVE or EXPIRING policy is in force today, a FUTURE one not yet.
 */
public enum PolicyStatus {

	/** Starts after today: recorded ahead, e.g. a renewal, but no cover yet. */
	FUTURE("Μελλοντικό"),
	ACTIVE("Ενεργό"),
	EXPIRING("Λήγει σύντομα"),
	EXPIRED("Ληγμένο");

	/** Same horizon as the dashboard's default view (SPEC §7.1). */
	public static final int EXPIRING_SOON_DAYS = 30;

	private final String label;

	PolicyStatus(String label) {
		this.label = label;
	}

	public static PolicyStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
		if (startDate.isAfter(today)) {
			return FUTURE;
		}
		if (endDate.isBefore(today)) {
			return EXPIRED;
		}
		return endDate.isAfter(today.plusDays(EXPIRING_SOON_DAYS)) ? ACTIVE : EXPIRING;
	}

	public String getLabel() {
		return label;
	}

	/** Covers the vehicle today, so the vehicle card puts it in emphasis. */
	public boolean isInForce() {
		return this == ACTIVE || this == EXPIRING;
	}

}
