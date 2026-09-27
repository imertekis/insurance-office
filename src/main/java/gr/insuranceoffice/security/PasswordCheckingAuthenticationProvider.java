package gr.insuranceoffice.security;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The usual username and password login against {@code app_user}, which
 * also checks the password that was just typed against
 * {@link PasswordPolicy} (Task 22a, decision 1). One that no longer meets it
 * still logs in, but the session is marked for {@link ForcedPasswordChange}.
 * <p>
 * The check is here, not in a success handler: Spring Security erases the
 * typed password once the login is done, and this is the last place that
 * sees it. The password itself goes nowhere else.
 * <p>
 * Before any of it, {@link LoginAttempts} refuses a name locked out after
 * too many failed logins (Task 22b).
 */
public class PasswordCheckingAuthenticationProvider extends DaoAuthenticationProvider {

	private final LoginAttempts loginAttempts;

	public PasswordCheckingAuthenticationProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, LoginAttempts loginAttempts) {
		super(userDetailsService);
		setPasswordEncoder(passwordEncoder);
		this.loginAttempts = loginAttempts;
	}

	// A locked-out name gets no further, not even to its account; every
	// other login with it is counted.
	@Override
	public Authentication authenticate(Authentication authentication) {
		return loginAttempts.authenticate(authentication.getName(), () -> super.authenticate(authentication));
	}

	@Override
	protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
			UserDetails user) {
		Authentication success = super.createSuccessAuthentication(principal, authentication, user);
		String typed = String.valueOf(authentication.getCredentials());
		return PasswordPolicy.check(typed, user.getUsername()).isPresent()
				? ForcedPasswordChange.with(success)
				: success;
	}

}
