package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.repository.AppUserRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomUserDetailsServiceTest {

	@Autowired
	private CustomUserDetailsService userDetailsService;

	@Autowired
	private AppUserRepository appUserRepository;

	@BeforeEach
	void startEmpty() {
		appUserRepository.deleteAll();
	}

	@AfterEach
	void leaveEmpty() {
		appUserRepository.deleteAll();
	}

	// The Greek role becomes the authority, with the usual ROLE_ prefix.
	@Test
	void loadsTheUserWithItsIdAndRole() {
		AppUser stored = user("maria", Role.ΔΙΑΧΕΙΡΙΣΤΗΣ, true);

		UserDetails details = userDetailsService.loadUserByUsername("maria");

		assertThat(details).isInstanceOfSatisfying(AppUserDetails.class,
				user -> assertThat(user.getId()).isEqualTo(stored.getId()));
		assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ΔΙΑΧΕΙΡΙΣΤΗΣ");
		assertThat(details.getPassword()).isEqualTo(stored.getPasswordHash());
		assertThat(details.isEnabled()).isTrue();
	}

	@Test
	void marksADeactivatedUserAsDisabled() {
		user("nikos", Role.ΥΠΑΛΛΗΛΟΣ, false);

		assertThat(userDetailsService.loadUserByUsername("nikos").isEnabled()).isFalse();
	}

	@Test
	void refusesAnUnknownUser() {
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("κανείς"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	private AppUser user(String username, Role role, boolean active) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
		user.setFullName("Δοκιμαστικός Χρήστης");
		user.setRole(role);
		user.setActive(active);
		return appUserRepository.save(user);
	}

}
