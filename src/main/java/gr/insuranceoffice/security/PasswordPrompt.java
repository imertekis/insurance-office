package gr.insuranceoffice.security;

import java.io.Console;
import java.util.Optional;

/**
 * Where the create-user profile reads a password: the console, without
 * showing what is typed (Task 22a, decision 8). Never the command line,
 * which stays in the shell's history and shows in the list of processes.
 * Tests give their own.
 */
@FunctionalInterface
public interface PasswordPrompt {

	/**
	 * @param prompt what to ask, in Greek
	 * @return what was typed, or empty when there is no console to type it
	 *         in, e.g. when the output goes to a pipe or a file
	 */
	Optional<char[]> read(String prompt);

	/** The console of the process, as {@link System#console()} gives it. */
	static PasswordPrompt console() {
		return prompt -> {
			Console console = System.console();
			if (console == null) {
				return Optional.empty();
			}
			// null when the input ends (Ctrl+D) before a line is typed: an
			// empty password, which the rule then refuses.
			char[] typed = console.readPassword("%s", prompt);
			return Optional.of(typed == null ? new char[0] : typed);
		};
	}

}
