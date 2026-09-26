package gr.insuranceoffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Task 22a: the rule of every password. Built from repeated characters, so
 * no password here is anyone's.
 */
class PasswordPolicyTest {

	@Test
	void takesEightCharactersWithALetterAndADigit() {
		assertThat(PasswordPolicy.check("a".repeat(7) + "1", "maria")).isEmpty();
		assertThat(PasswordPolicy.check("a".repeat(6) + "1", "maria")).contains(PasswordPolicy.TOO_WEAK);
	}

	@ParameterizedTest(name = "«{0}»")
	@ValueSource(strings = { "", "abcdefgh", "αβγδεζηθ", "12345678", "        ", "!!!!!!!1" })
	void refusesAPasswordWithoutALetterOrADigit(String password) {
		assertThat(PasswordPolicy.check(password, "maria")).contains(PasswordPolicy.TOO_WEAK);
	}

	@Test
	void refusesNoPasswordAtAll() {
		assertThat(PasswordPolicy.check(null, "maria")).contains(PasswordPolicy.TOO_WEAK);
	}

	// Greek letters count as letters, and as one character each.
	@Test
	void countsGreekLettersAsLettersAndAsOneCharacterEach() {
		assertThat(PasswordPolicy.check("αβγδεζη7", "maria")).isEmpty();
		assertThat(PasswordPolicy.check("αβγδεζ7", "maria")).contains(PasswordPolicy.TOO_WEAK);
	}

	// Spaces are allowed and count; nothing is trimmed.
	@Test
	void countsSpacesLikeAnyCharacter() {
		assertThat(PasswordPolicy.check("a b c d 1", "maria")).isEmpty();
		assertThat(PasswordPolicy.check("   ab1  ", "maria")).isEmpty();
		assertThat(PasswordPolicy.check("  ab1  ", "maria")).contains(PasswordPolicy.TOO_WEAK);
	}

	// bcrypt reads 72 bytes; a Greek letter is two of them in UTF-8.
	@Test
	void refusesMoreThanSeventyTwoBytes() {
		String exactly = "α".repeat(35) + "1" + "b";
		String over = "α".repeat(36) + "1";
		assertThat(exactly.getBytes(StandardCharsets.UTF_8)).hasSize(72);
		assertThat(over.getBytes(StandardCharsets.UTF_8)).hasSize(73);

		assertThat(PasswordPolicy.check(exactly, "maria")).isEmpty();
		assertThat(PasswordPolicy.check(over, "maria")).contains(PasswordPolicy.TOO_LONG);
		assertThat(PasswordPolicy.check("a".repeat(72) + "1", "maria")).contains(PasswordPolicy.TOO_LONG);
	}

	@Test
	void refusesTheUsernameWhateverItsCase() {
		assertThat(PasswordPolicy.check("maria2024", "maria2024")).contains(PasswordPolicy.SAME_AS_USERNAME);
		assertThat(PasswordPolicy.check("MARIA2024", "maria2024")).contains(PasswordPolicy.SAME_AS_USERNAME);
		assertThat(PasswordPolicy.check("Μαρία2024", "ΜΑΡΊΑ2024")).contains(PasswordPolicy.SAME_AS_USERNAME);
		assertThat(PasswordPolicy.check("maria2024!", "maria2024")).isEmpty();
	}

}
