package gr.insuranceoffice.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who is making the current change, for the audit log. Empty when nobody is
 * logged in, as in the Excel import or a scheduled job.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<Long> id() {
		return appUser().map(AppUserDetails::getId);
	}

	/** The name to show in the header: the full name, or the username. */
	public static Optional<String> displayName() {
		Authentication authentication = authentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		return appUser().map(AppUserDetails::getFullName).or(() -> Optional.ofNullable(authentication.getName()));
	}

	/** Whether the logged-in user may delete (SPEC §2, Task 11e). */
	public static boolean isAdministrator() {
		Authentication authentication = authentication();
		return authentication != null && authentication.isAuthenticated()
				&& authentication.getAuthorities().stream()
						.anyMatch(authority -> Roles.ADMINISTRATOR_AUTHORITY.equals(authority.getAuthority()));
	}

	private static Optional<AppUserDetails> appUser() {
		Authentication authentication = authentication();
		return authentication != null && authentication.getPrincipal() instanceof AppUserDetails user
				? Optional.of(user)
				: Optional.empty();
	}

	private static Authentication authentication() {
		return SecurityContextHolder.getContext().getAuthentication();
	}

}
