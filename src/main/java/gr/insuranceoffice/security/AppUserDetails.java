package gr.insuranceoffice.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import gr.insuranceoffice.entity.AppUser;

/**
 * The logged-in clerk. Carries the {@code app_user} id as well as the name,
 * because the audit log records who made a change by id (ARCHITECTURE §6).
 * The role becomes an authority with the usual {@code ROLE_} prefix, keeping
 * its Greek name: {@code ROLE_ΥΠΑΛΛΗΛΟΣ}, {@code ROLE_ΔΙΑΧΕΙΡΙΣΤΗΣ}.
 */
public class AppUserDetails implements UserDetails {

	private final Long id;

	private final String username;

	private final String passwordHash;

	private final String fullName;

	private final AppUser.Role role;

	private final boolean active;

	public AppUserDetails(AppUser user) {
		this.id = user.getId();
		this.username = user.getUsername();
		this.passwordHash = user.getPasswordHash();
		this.fullName = user.getFullName();
		this.role = user.getRole();
		this.active = user.isActive();
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public AppUser.Role getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

}
