package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;

/**
 * Task 16c, the part the server renders. What the theme does in the browser
 * (the OS following, the menu clicks) is checked visually, as TASKS asks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class ThemeTest {

	@Autowired
	private MockMvc mockMvc;

	// Set in <head>, so a dark page is never drawn white first; the login page
	// has no header but the same <head>.
	@Test
	void setsTheThemeBeforeThePageIsDrawnOnEveryPage() throws Exception {
		for (String page : List.of("/login", "/", "/customers", "/vehicles/new")) {
			String html = html(page);
			int headEnd = html.indexOf("</head>");
			assertThat(html.indexOf("setAttribute(\"data-bs-theme\"")).as(page).isPositive().isLessThan(headEnd);
			assertThat(html.indexOf("src=\"/js/theme.js\"")).as(page).isPositive().isLessThan(headEnd);
		}
	}

	// Loaded by the login page too, before anyone has logged in; a redirect to
	// the login form here would leave that page deaf to the OS.
	@Test
	@WithAnonymousUser
	void servesTheThemeScriptBeforeLogin() throws Exception {
		mockMvc.perform(get("/js/theme.js")).andExpect(status().isOk());
	}

	// The calendar's dark theme is a whole second stylesheet: off until the
	// page is dark.
	@Test
	void keepsTheDarkCalendarStylesheetOffUntilThePageIsDark() throws Exception {
		assertThat(html("/vehicles/new")).contains("id=\"flatpickr-dark\" media=\"not all\"",
				"/webjars/flatpickr/4.6.13/dist/themes/dark.css");
	}

	// Task 16f-1: its own button, on every page and on the login page, which
	// has no header; one of each choice, each with its icon.
	@Test
	void offersTheThemesInTheirOwnButtonOnEveryPage() throws Exception {
		for (String page : List.of("/login", "/", "/customers", "/vehicles/new")) {
			String html = html(page);
			assertThat(count(html, "aria-label=\"Θέμα\"")).as(page).isEqualTo(1);
			for (String choice : List.of("auto", "light", "dark")) {
				assertThat(count(html, "data-theme-choice=\"" + choice + "\"")).as(page + " " + choice).isEqualTo(1);
				assertThat(html).as(page).contains("<use href=\"#theme-icon-" + choice + "\"/></svg>");
			}
			assertThat(html).as(page).contains("Αυτόματο</button>", "Φωτεινό</button>", "Σκούρο</button>");
		}
	}

	// One place for the theme: the user menu has the name and the way out.
	@Test
	void leavesTheUserMenuWithTheNameAndLogoutOnly() throws Exception {
		String html = html("/");
		String userMenu = html.substring(html.indexOf("id=\"header-user\""), html.indexOf("</nav>"));
		assertThat(userMenu).contains("Αποσύνδεση").doesNotContain("data-theme-choice", "Θέμα");
	}

	// The icon shows the choice from the first paint: the inline script puts
	// it on <html> before the body is read.
	@Test
	void marksTheChoiceBeforeThePageIsDrawn() throws Exception {
		for (String page : List.of("/login", "/")) {
			String html = html(page);
			assertThat(html.indexOf("setAttribute(\"data-chosen-theme\", chosen)")).as(page)
					.isPositive().isLessThan(html.indexOf("</head>"));
		}
	}

	// Black on near-white in both themes: the brightest thing on a dark page.
	@Test
	void noTemplateUsesTheLightOnlyBadge() throws IOException {
		Resource[] templates = new PathMatchingResourcePatternResolver()
				.getResources("classpath*:templates/**/*.html");
		assertThat(templates).isNotEmpty();
		for (Resource template : templates) {
			assertThat(template.getContentAsString(StandardCharsets.UTF_8)).as(template.getFilename())
					.doesNotContain("text-bg-light");
		}
	}

	private static int count(String html, String text) {
		return html.split(Pattern.quote(text), -1).length - 1;
	}

	private String html(String url) throws Exception {
		return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

}
