package gr.insuranceoffice.security;

/**
 * The two roles of SPEC §2, as Spring Security sees them. Every logged-in
 * user searches, views, creates and edits; deleting is for the ΔΙΑΧΕΙΡΙΣΤΗΣ
 * alone (Task 11e).
 */
public final class Roles {

	/** For {@code @PreAuthorize} on every delete, in the services. */
	public static final String ADMINISTRATOR_ONLY = "hasRole('ΔΙΑΧΕΙΡΙΣΤΗΣ')";

	static final String ADMINISTRATOR_AUTHORITY = "ROLE_ΔΙΑΧΕΙΡΙΣΤΗΣ";

	private Roles() {
	}

}
