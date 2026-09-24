package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
 * Task 16d-2: the tab icon, on every page, the login page included. What it
 * looks like is checked visually, as TASKS asks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class FaviconTest {

	@Autowired
	private MockMvc mockMvc;

	// Asked for by the login page, before anyone has logged in: a redirect
	// here would hand the browser the login form instead of an image.
	@Test
	@WithAnonymousUser
	void servesTheIconsBeforeLogin() throws Exception {
		mockMvc.perform(get("/favicon.svg"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
		String ico = mockMvc.perform(get("/favicon.ico"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentType();
		assertThat(ico).startsWith("image/");
	}

	// Allowed by exact path, not "/*": anything next to them still needs a
	// login.
	@Test
	@WithAnonymousUser
	void keepsEveryOtherPathBehindTheLogin() throws Exception {
		for (String path : List.of("/favicon.png", "/favicon.ico/", "/favicon.svg/x", "/robots.txt")) {
			mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
	}

	@Test
	void linksTheIconsOnEveryPage() throws Exception {
		for (String page : List.of("/login", "/", "/customers", "/vehicles/new")) {
			String html = mockMvc.perform(get(page)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			int headEnd = html.indexOf("</head>");
			assertThat(html.indexOf("<link rel=\"icon\" href=\"/favicon.svg\" type=\"image/svg+xml\">")).as(page)
					.isPositive().isLessThan(headEnd);
			assertThat(html.indexOf("<link rel=\"icon\" href=\"/favicon.ico\"")).as(page)
					.isPositive().isLessThan(headEnd);
		}
	}

}
