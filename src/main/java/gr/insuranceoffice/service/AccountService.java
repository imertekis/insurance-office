package gr.insuranceoffice.service;

import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.security.PasswordPolicy;

/**
 * The logged-in user's own account: «Αλλαγή κωδικού» (Task 22a). The rule
 * is {@link PasswordPolicy}'s; what needs the stored hash (the current
 * password, a new one equal to it) is checked here.
 * <p>
 * No password or hash goes to a message, the log or {@code audit_log}:
 * {@code app_user} has no audit yet (decision 7).
 */
@Service
public class AccountService {

	private final AppUserRepository appUserRepository;

	private final PasswordEncoder passwordEncoder;

	public AccountService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * @throws BusinessException with a message per field, keyed
	 *             {@code currentPassword}, {@code newPassword},
	 *             {@code newPasswordAgain}; nothing is changed
	 * @throws NotFoundException if the account is gone
	 */
	@Transactional
	public void changePassword(Long userId, String currentPassword, String newPassword, String newPasswordAgain) {
		AppUser user = appUserRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("Ο λογαριασμός δεν βρέθηκε."));

		Violations violations = new Violations();
		boolean currentRight = currentPassword != null && !currentPassword.isEmpty()
				&& passwordEncoder.matches(currentPassword, user.getPasswordHash());
		violations.addIf(!currentRight, "currentPassword", "Ο τρέχων κωδικός δεν είναι σωστός.");
		PasswordPolicy.check(newPassword, user.getUsername())
				.ifPresentOrElse(problem -> violations.add("newPassword", problem),
						() -> violations.addIf(passwordEncoder.matches(newPassword, user.getPasswordHash()),
								"newPassword", PasswordPolicy.SAME_AS_CURRENT));
		violations.addIf(!Objects.equals(newPassword, newPasswordAgain), "newPasswordAgain",
				"Οι δύο νέοι κωδικοί δεν είναι ίδιοι.");
		violations.throwIfAny();

		user.setPasswordHash(passwordEncoder.encode(newPassword));
	}

}
