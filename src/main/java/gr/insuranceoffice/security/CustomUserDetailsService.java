package gr.insuranceoffice.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.repository.AppUserRepository;

/** Authenticates against {@code app_user} (SPEC §2). */
@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public CustomUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		return appUserRepository.findByUsername(username)
				.map(AppUserDetails::new)
				// This message is never shown: the login provider turns the
				// exception into the wrong-password one, and the login page
				// has one message for both, so it confirms no username.
				.orElseThrow(() -> new UsernameNotFoundException("Άγνωστος χρήστης"));
	}

}
