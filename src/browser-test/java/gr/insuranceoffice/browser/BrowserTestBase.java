package gr.insuranceoffice.browser;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.AriaRole;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;

/**
 * Task 24: what the tests of app.js share. The application runs on a port of
 * its own, on the Testcontainers PostgreSQL, with synthetic data each test
 * makes; each test gets a fresh browser context (no cookies, no storage) and
 * an account with a random password, made here and never written anywhere.
 * <p>
 * Chromium only (decision 2), headless, in the version Playwright pins
 * (decision 3). Playwright's own first start would download Firefox and
 * WebKit too, so it is told not to, and only Chromium's headless build is
 * installed, into ~/.cache/ms-playwright; once there, installing it again
 * does nothing.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class BrowserTestBase {

	private static final String USERNAME = "δοκιμή";

	/** The name the header shows for the account the tests log in with. */
	protected static final String FULL_NAME = "Υπάλληλος Δοκιμής";

	private static final Map<String, String> NO_DEFAULT_BROWSERS = Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

	private static final SecureRandom RANDOM = new SecureRandom();

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

	@Autowired
	protected CustomerRepository customerRepository;

	@Autowired
	protected VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	protected PolicyRepository policyRepository;

	private String password;

	protected BrowserContext context;

	protected Page page;

	// Through the driver Playwright itself uses: its command-line class ends
	// the JVM when done.
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
		PlaywrightAssertions.setDefaultAssertionTimeout(5_000);
	}

	@AfterAll
	static void closeChromium() {
		if (playwright != null) {
			playwright.close();
		}
	}

	@BeforeEach
	void openBrowserWithAnAccount() {
		emptyTables();
		password = randomPassword();
		AppUser user = new AppUser();
		user.setUsername(USERNAME);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setFullName(FULL_NAME);
		user.setRole(Role.ΥΠΑΛΛΗΛΟΣ);
		appUserRepository.save(user);
		open(contextOptions());
	}

	// Other test classes share this database.
	@AfterEach
	void closeBrowserAndEmptyTables() {
		if (context != null) {
			context.close();
		}
		emptyTables();
	}

	/** Greek, in the office's time zone; a test class may choose otherwise. */
	protected Browser.NewContextOptions contextOptions() {
		return new Browser.NewContextOptions().setLocale("el-GR").setTimezoneId("Europe/Athens");
	}

	/**
	 * A phone of this size, with touch, for a test of its own: a new context
	 * and tab in place of the ones the test started with, before logging in.
	 */
	protected void usePhone(int width, int height) {
		context.close();
		open(contextOptions().setViewportSize(width, height).setIsMobile(true).setHasTouch(true));
	}

	private void open(Browser.NewContextOptions options) {
		context = browser.newContext(options.setBaseURL("http://localhost:" + port));
		context.setDefaultTimeout(10_000);
		page = context.newPage();
	}

	/** Logs in through the login page, then opens the page asked for. */
	protected void logInAndOpen(String path) {
		page.navigate("/login");
		logIn(page);
		page.navigate(path);
	}

	/** Fills in the login page the given tab shows, and sends it. */
	protected void logIn(Page tab) {
		tab.locator("#username").fill(USERNAME);
		tab.locator("#password").fill(password);
		tab.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Σύνδεση")).click();
		tab.waitForURL(url -> !url.contains("/login"));
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}

	protected Customer customer(String lastName, String firstName, String taxId, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		customer.setMobile(mobile);
		return customerRepository.save(customer);
	}

	protected Vehicle vehicle(String plate, String vin, String brand, String model) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand(brand);
		vehicle.setModel(model);
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	protected void owns(Vehicle vehicle, Customer customer, String percentage, boolean primary) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal(percentage));
		ownership.setPrimary(primary);
		ownershipRepository.save(ownership);
	}

	protected Policy policy(Vehicle vehicle, String policyNumber, LocalDate start, LocalDate end) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(start);
		policy.setEndDate(end);
		policy.setPremium(new BigDecimal("180.00"));
		return policyRepository.save(policy);
	}

	private void emptyTables() {
		jdbcTemplate.execute("TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer, app_user "
				+ "RESTART IDENTITY CASCADE");
	}

	// Meets the rule of Task 22a (a letter, a digit, 8 characters or more)
	// whatever the random part holds.
	private static String randomPassword() {
		byte[] bytes = new byte[18];
		RANDOM.nextBytes(bytes);
		return "a1" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
