package gr.insuranceoffice.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * The rule every password must meet (Task 22a), in one place: the
 * create-user profile, «Αλλαγή κωδικού», and the check of an existing
 * password at login all use it.
 * <ul>
 * <li>At least 8 characters, with a letter (Greek or Latin) and a digit.
 * Spaces are allowed and count like any character; nothing is trimmed.</li>
 * <li>At most 72 bytes in UTF-8, some 36 Greek letters. bcrypt reads no
 * further: the encoder refuses a longer password, and a login would ignore
 * whatever came after the 72nd byte.</li>
 * <li>Not the username, whatever the case of its letters.</li>
 * </ul>
 * That a new password differs from the current one needs the stored hash,
 * so its callers check it, with {@link #SAME_AS_CURRENT}.
 */
public final class PasswordPolicy {

	static final int MIN_CHARACTERS = 8;

	static final int MAX_BYTES = 72;

	public static final String TOO_WEAK =
			"Ο κωδικός πρέπει να έχει τουλάχιστον 8 χαρακτήρες, με ένα τουλάχιστον γράμμα και ένα ψηφίο.";

	public static final String TOO_LONG = "Ο κωδικός είναι πολύ μεγάλος.";

	public static final String SAME_AS_USERNAME = "Ο κωδικός δεν μπορεί να είναι το όνομα χρήστη.";

	public static final String SAME_AS_CURRENT = "Ο νέος κωδικός πρέπει να διαφέρει από τον τρέχοντα.";

	private PasswordPolicy() {
	}

	/**
	 * @param password as typed
	 * @param username the account's username
	 * @return what the password breaks, in Greek, or empty when it may be set
	 */
	public static Optional<String> check(String password, String username) {
		String typed = password == null ? "" : password;
		if (typed.codePointCount(0, typed.length()) < MIN_CHARACTERS
				|| typed.codePoints().noneMatch(Character::isLetter)
				|| typed.codePoints().noneMatch(Character::isDigit)) {
			return Optional.of(TOO_WEAK);
		}
		if (typed.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return Optional.of(TOO_LONG);
		}
		if (username != null && typed.toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT))) {
			return Optional.of(SAME_AS_USERNAME);
		}
		return Optional.empty();
	}

}
