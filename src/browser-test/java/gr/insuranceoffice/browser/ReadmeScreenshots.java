package gr.insuranceoffice.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotCaret;
import com.microsoft.playwright.options.WaitForSelectorState;

import gr.insuranceoffice.demo.DemoRunner;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.repository.AppUserRepository;

/**
 * Task 39c: the pictures of the README, in docs/images, from the data of the
 * demo profile (Task 39b), so that they show the current pages and nothing
 * real. Not a test: its name matches none of the patterns Surefire runs, so
 * {@code ./mvnw verify -Pbrowser} leaves it out, and it runs only when asked:
 *
 * <pre>
 * ./mvnw test -Pbrowser -Dtest=ReadmeScreenshots
 * </pre>
 *
 * Three pages, each in the light theme, the dark theme and on a phone of
 * 375px: the home screen, the search with its suggestions, a vehicle card.
 * The same seed gives the same people and vehicles on every run; the dates
 * are counted from the day it runs. Pages and crops are chosen to show few
 * ΑΦΜ and mobile numbers, although all of them are synthetic: the home
 * screen's 7 days, a search by model, whose suggestions name no ΑΦΜ, and a
 * vehicle whose one owner has no ΑΦΜ (DECISIONS §2).
 * <p>
 * Chromium as the browser tests launch it ({@link BrowserTestBase}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import(ReadmeScreenshots.DemoDatabase.class)
class ReadmeScreenshots {

	/** The only database the demo profile fills: its own, by name. */
	@TestConfiguration(proxyBeanMethods = false)
	static class DemoDatabase {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgresContainer() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
					.withDatabaseName(DemoRunner.DATABASE_NAME);
		}

	}

	private static final Path IMAGES = Path.of("docs", "images");

	/** One of the demo's two accounts (DemoSeeder): the clerk, who uses the application every day. */
	private static final String USERNAME = "clerk";

	private static final Map<String, String> NO_DEFAULT_BROWSERS = Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

	private static final SecureRandom RANDOM = new SecureRandom();

	/** How each page is shown: its file name ends with the look's name. */
	private enum Look {

		LIGHT(1440, 900, ColorScheme.LIGHT, false),
		DARK(1440, 900, ColorScheme.DARK, false),
		PHONE(375, 812, ColorScheme.LIGHT, true);

		final int width;
		final int height;
		final ColorScheme scheme;
		final boolean phone;

		Look(int width, int height, ColorScheme scheme, boolean phone) {
			this.width = width;
			this.height = height;
			this.scheme = scheme;
			this.phone = phone;
		}

	}

	private static Playwright playwright;

	private static Browser browser;

	@LocalServerPort
	private int port;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String password;

	@BeforeAll
	static void launchChromium() throws IOException, InterruptedException {
		ProcessBuilder install = Driver.ensureDriverInstalled(NO_DEFAULT_BROWSERS, false).createProcessBuilder();
		install.command().addAll(List.of("install", "--only-shell", "chromium"));
		install.inheritIO();
		int exit = install.start().waitFor();
		if (exit != 0) {
			throw new IllegalStateException("Could not install Chromium for Playwright (exit code " + exit + ")");
		}
		playwright = Playwright.create(new Playwright.CreateOptions().setEnv(NO_DEFAULT_BROWSERS));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
		Files.createDirectories(IMAGES);
	}

	@AfterAll
	static void closeChromium() {
		if (playwright != null) {
			playwright.close();
		}
	}

	// The demo printed the clerk's password in the log; a new random one here
	// saves reading it back, and is written nowhere.
	@BeforeEach
	void setThePassword() {
		byte[] bytes = new byte[18];
		RANDOM.nextBytes(bytes);
		password = "a1" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		AppUser clerk = appUserRepository.findByUsername(USERNAME).orElseThrow();
		clerk.setPasswordHash(passwordEncoder.encode(password));
		appUserRepository.save(clerk);
	}

	/** SPEC §7.1: the renewals to do; the 7 days, the most urgent and the fewest rows. */
	@Test
	void homeScreen() {
		for (Look look : Look.values()) {
			shoot("home", look, page -> page.navigate("/?period=7"), ReadmeScreenshots::pageBottom);
		}
	}

	/**
	 * Task 21b: suggestions while typing, over the vehicle list, which shows
	 * no ΑΦΜ or mobile. A model, typed in lower case: the vehicles' own
	 * suggestions name the owner, not the ΑΦΜ as a customer's do.
	 */
	@Test
	void searchWithSuggestions() {
		for (Look look : Look.values()) {
			shoot("search", look, page -> {
				page.navigate("/vehicles");
				page.locator("#q").pressSequentially("yaris");
				page.locator("#search-suggestions .search-suggestion").first()
						.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
			}, page -> bottom(page, "#search-suggestions") + 90);
		}
	}

	/**
	 * SPEC §7.2: a vehicle with its owner and its renewals, in force today.
	 * Its one owner has no ΑΦΜ, as some customers do (DECISIONS §2).
	 */
	@Test
	void vehicleCard() {
		Long vehicle = jdbcTemplate.queryForObject("""
				SELECT v.id FROM vehicle v
				JOIN ownership o ON o.vehicle_id = v.id
				JOIN customer c ON c.id = o.customer_id
				WHERE c.tax_id IS NULL AND c.mobile IS NOT NULL
				AND (SELECT count(*) FROM ownership other WHERE other.vehicle_id = v.id) = 1
				AND EXISTS (SELECT 1 FROM policy p
						WHERE p.vehicle_id = v.id AND p.start_date <= ? AND p.end_date >= ?)
				ORDER BY (SELECT count(*) FROM policy p WHERE p.vehicle_id = v.id) DESC, v.id
				LIMIT 1
				""", Long.class, LocalDate.now(), LocalDate.now());
		for (Look look : Look.values()) {
			shoot("vehicle", look, page -> page.navigate("/vehicles/" + vehicle), ReadmeScreenshots::pageBottom);
		}
	}

	/**
	 * Logs in, lets {@code show} open the page, and saves it as
	 * docs/images/{name}-{look}.png: on a phone, its screen; otherwise the page
	 * down to where {@code height} says, however long.
	 */
	private void shoot(String name, Look look, Consumer<Page> show, ToDoubleFunction<Page> height) {
		Browser.NewContextOptions options = new Browser.NewContextOptions()
				.setBaseURL("http://localhost:" + port)
				.setLocale("el-GR")
				.setTimezoneId("Europe/Athens")
				.setColorScheme(look.scheme)
				.setViewportSize(look.width, look.height)
				.setIsMobile(look.phone)
				.setHasTouch(look.phone);
		try (BrowserContext context = browser.newContext(options)) {
			context.setDefaultTimeout(10_000);
			Page page = context.newPage();
			page.navigate("/login");
			page.locator("#username").fill(USERNAME);
			page.locator("#password").fill(password);
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Σύνδεση")).click();
			page.waitForURL(url -> !url.contains("/login"));
			show.accept(page);
			Page.ScreenshotOptions screenshot = new Page.ScreenshotOptions()
					.setPath(IMAGES.resolve(name + "-" + look.name().toLowerCase() + ".png"))
					.setAnimations(ScreenshotAnimations.DISABLED)
					.setCaret(ScreenshotCaret.HIDE);
			if (!look.phone) {
				screenshot.setFullPage(true).setClip(0, 0, look.width, Math.ceil(height.applyAsDouble(page)));
			}
			page.screenshot(screenshot);
		}
	}

	// Where the page's own content ends, with the margin under it: no empty
	// band below a short page.
	private static double pageBottom(Page page) {
		return ((Number) page.evaluate("Math.max(...[...document.body.children]"
				+ ".map(element => element.getBoundingClientRect().bottom + window.scrollY))")).doubleValue() + 32;
	}

	private static double bottom(Page page, String selector) {
		BoundingBox box = page.locator(selector).boundingBox();
		return box.y + box.height;
	}

}
