package gr.insuranceoffice.dto;

import java.time.LocalDate;

/** How a policy stands today, for the coloured badges of SPEC §7.2 and §7.3. */
public enum PolicyStatus {

	ACTIVE("Ενεργό"),
	EXPIRING("Λήγει σύντομα"),
	EXPIRED("Ληγμένο");

	/** Same horizon as the dashboard's default view (SPEC §7.1). */
	public static final int EXPIRING_SOON_DAYS = 30;

	private final String label;

	PolicyStatus(String label) {
		this.label = label;
	}

	public static PolicyStatus of(LocalDate endDate, LocalDate today) {
		if (endDate.isBefore(today)) {
			return EXPIRED;
		}
		return endDate.isAfter(today.plusDays(EXPIRING_SOON_DAYS)) ? ACTIVE : EXPIRING;
	}

	public String getLabel() {
		return label;
	}

}
