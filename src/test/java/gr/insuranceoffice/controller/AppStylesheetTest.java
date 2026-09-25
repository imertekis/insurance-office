package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;

/**
 * Task 16d-1, the part the server renders: our stylesheet reaches every page,
 * the login page included. What it looks like is checked visually, as TASKS
 * asks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class AppStylesheetTest {

	@Autowired
	private MockMvc mockMvc;

	// Loaded by the login page, before anyone has logged in; a redirect to
	// the login form here would leave that page without field borders.
	@Test
	@WithAnonymousUser
	void servesTheStylesheetBeforeLogin() throws Exception {
		mockMvc.perform(get("/css/app.css"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	// Task 20: a former owner's or an expired policy's row is faint through
	// the variable its cells read. A colour on the row itself never reached
	// them: Bootstrap gives every table cell its own. Under the mouse too.
	@Test
	void mutesATableRowThroughTheVariableItsCellsRead() throws Exception {
		String css = mockMvc.perform(get("/css/app.css")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(css).containsPattern("\\.row-muted \\{\\s*--bs-table-color: var\\(--bs-secondary-color\\);"
				+ "\\s*--bs-table-hover-color: var\\(--bs-secondary-color\\);\\s*\\}");
	}

	// After Bootstrap, so it can add to it.
	@Test
	void linksTheStylesheetAfterBootstrapOnEveryPage() throws Exception {
		for (String page : List.of("/login", "/", "/customers/new", "/vehicles/new")) {
			String html = mockMvc.perform(get(page)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			int headEnd = html.indexOf("</head>");
			int bootstrap = html.indexOf("bootstrap.min.css");
			int ours = html.indexOf("href=\"/css/app.css\"");
			assertThat(bootstrap).as(page).isPositive();
			assertThat(ours).as(page).isGreaterThan(bootstrap).isLessThan(headEnd);
		}
	}

}
