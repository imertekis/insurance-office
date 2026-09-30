package gr.insuranceoffice.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * A login whose password no longer meets {@link PasswordPolicy} (Task 22a,
 * decision 1): the session carries one more authority, and until the
 * password is changed every page leads to «Αλλαγή κωδικού»
 * ({@link ForcedPasswordChangeFilter}). The rule can only be checked at
 * login, the one moment the password is known; a stored bcrypt hash tells
 * nothing about it.
 */
public final class ForcedPasswordChange {

	/** Not a role: no page is ever granted with it. */
	static final String AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

	private ForcedPasswordChange() {
	}

	/** Whether the logged-in user must change their password before anything else. */
	public static boolean required() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> AUTHORITY.equals(authority.getAuthority()));
	}

	static Authentication with(Authentication authentication) {
		List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
		authorities.add(new SimpleGrantedAuthority(AUTHORITY));
		return copy(authentication, authorities);
	}

	/**
	 * The password has been changed: the same login goes on, without the
	 * restriction, in this request and the next ones. The session keeps its
	 * id; the other sessions of the user are left as they are (Task 22, decision 6).
	 */
	public static void lift(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !required()) {
			return;
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(copy(authentication, authentication.getAuthorities().stream()
				.filter(authority -> !AUTHORITY.equals(authority.getAuthority()))
				.map(GrantedAuthority.class::cast)
				.toList()));
		SecurityContextHolder.setContext(context);
		// Where the login keeps the context between requests.
		new HttpSessionSecurityContextRepository().saveContext(context, request, response);
	}

	private static Authentication copy(Authentication authentication, List<GrantedAuthority> authorities) {
		UsernamePasswordAuthenticationToken copy = UsernamePasswordAuthenticationToken
				.authenticated(authentication.getPrincipal(), authentication.getCredentials(), authorities);
		copy.setDetails(authentication.getDetails());
		return copy;
	}

}
