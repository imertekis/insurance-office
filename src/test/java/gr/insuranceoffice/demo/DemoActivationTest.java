package gr.insuranceoffice.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Task 39b: the demo profile is activated by compose.demo.yaml, and by
 * nothing the office server runs from (Task 31). Read as text: whatever
 * activates a Spring profile, an environment variable, an argument or a
 * property, names "profiles".
 */
class DemoActivationTest {

	@ParameterizedTest
	@ValueSource(strings = { "deploy/compose.yaml", "deploy/.env.example", "deploy/initdb/10-app-role.sh" })
	void theServerFilesActivateNoProfile(String file) throws IOException {
		assertThat(settings(file)).isNotEmpty()
				.noneMatch(line -> line.toLowerCase().contains("profiles"))
				.noneMatch(line -> line.toLowerCase().contains("demo"));
	}

	// The image of the server is the last stage, built without --target; no
	// stage sets a profile, the demo's included: compose.demo.yaml does.
	@Test
	void theServerImageIsTheDefaultOneAndNoImageSetsAProfile() throws IOException {
		List<String> dockerfile = settings("Dockerfile");

		assertThat(dockerfile).noneMatch(line -> line.toLowerCase().contains("profiles"));
		assertThat(dockerfile.stream().filter(line -> line.startsWith("FROM ")).toList().getLast())
				.endsWith(" AS server");
	}

	@Test
	void theDemoComposeFileActivatesTheDemoOnItsOwnDatabase() throws IOException {
		List<String> compose = settings("compose.demo.yaml");

		assertThat(compose).contains("      SPRING_PROFILES_ACTIVE: demo",
				"      POSTGRES_DB: " + DemoRunner.DATABASE_NAME, "      DB_NAME: " + DemoRunner.DATABASE_NAME);
	}

	// The lines that set something: comments and blank lines left out.
	private static List<String> settings(String file) throws IOException {
		return Files.readAllLines(Path.of(file)).stream()
				.filter(line -> !line.isBlank() && !line.strip().startsWith("#"))
				.toList();
	}

}
