package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.PageDto;
import gr.insuranceoffice.dto.PolicyListDto;
import gr.insuranceoffice.dto.PolicyViewDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.dto.SortDirection;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.CustomerService;
import gr.insuranceoffice.service.PolicyService;
import gr.insuranceoffice.service.VehicleService;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * The list pages of Task 15: paged, sorted, and linked from the header. The
 * services are asked for the order and the page arithmetic, the pages for
 * what the clerk sees.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(TestcontainersConfiguration.class)
class ListPagesTest {

	private static final LocalDate TODAY = LocalDate.now();

	private static final int PAGE_SIZE = 50;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerService customerService;

	@Autowired
	private VehicleService vehicleService;

	@Autowired
	private PolicyService policyService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private OwnershipRepository ownershipRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void startEmpty() {
		truncateTables();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Nested
	class Customers {

		// The Greek alphabet order, with the accented capitals and the lower
		// case among the rest. A code point order would put Άγγελος, Έλενα and
		// Ήρα before Α and αθανασίου after Ω.
		private final List<String> inGreekOrder = List.of("Αβραμίδης", "Άγγελος", "αθανασίου", "Αλεξίου",
				"Βασιλείου", "Δημητρίου", "Έλενα", "Ζήσης", "Ηλίας", "Ήρα", "Ωραίος");

		@Test
		void sortsNamesInGreekAlphabeticalOrderWithAccentedCapitalsAmongTheirLetters() {
			inGreekOrder.reversed().forEach(name -> customer(name, null));

			assertThat(lastNames(customerService.list(SortDirection.ASC, 1))).containsExactlyElementsOf(inGreekOrder);
		}

		@Test
		void sortsOmegaToAlphaByTurningTheOrderRound() {
			inGreekOrder.forEach(name -> customer(name, null));

			assertThat(lastNames(customerService.list(SortDirection.DESC, 1)))
					.containsExactlyElementsOf(inGreekOrder.reversed());
		}

		@Test
		void putsALatinNameAfterTheGreekOnesInAlphaToOmega() {
			customer("Smith", "John");
			customer("Ωραίος", "Νίκος");
			customer("Άγγελος", "Πέτρος");

			assertThat(lastNames(customerService.list(SortDirection.ASC, 1)))
					.containsExactly("Άγγελος", "Ωραίος", "Smith");
		}

		@Test
		void breaksTiesWithTheFirstNameAndIgnoresCaseAndAccents() {
			customer("ΑΛΕΞΙΟΥ", "Νίκος");
			customer("Αλεξίου", "Μαρία");
			customer("Αλεξίου", "Άννα");
			customer("Αλεξίου", null);

			assertThat(customerService.list(SortDirection.ASC, 1).items())
					.extracting(hit -> hit.lastName() + "/" + hit.firstName())
					.containsExactly("Αλεξίου/null", "Αλεξίου/Άννα", "Αλεξίου/Μαρία", "ΑΛΕΞΙΟΥ/Νίκος");
		}

		// Rows written outside JPA (import, manual SQL) get their sort key too.
		@Test
		void sortsRowsInsertedWithPlainSql() {
			jdbcTemplate.update("INSERT INTO customer (last_name) VALUES ('Βασιλείου'), ('Άγγελος'), ('Αβραμίδης')");

			assertThat(lastNames(customerService.list(SortDirection.ASC, 1)))
					.containsExactly("Αβραμίδης", "Άγγελος", "Βασιλείου");
		}

		@Test
		void showsOnlyOnePageAndSaysWhichRows() throws Exception {
			manyCustomers(120);

			PageDto<CustomerHit> first = customerService.list(SortDirection.ASC, 1);
			PageDto<CustomerHit> last = customerService.list(SortDirection.ASC, 3);

			assertThat(first.items()).hasSize(PAGE_SIZE);
			assertThat(first.items().get(0).lastName()).isEqualTo("Πελάτης 001");
			assertThat(first.totalItems()).isEqualTo(120);
			assertThat(first.getTotalPages()).isEqualTo(3);
			assertThat(last.items()).hasSize(20);
			assertThat(last.items().get(19).lastName()).isEqualTo("Πελάτης 120");
			assertThat(customerService.list(SortDirection.DESC, 1).items().get(0).lastName())
					.isEqualTo("Πελάτης 120");

			String html = html("/customers?page=2");
			assertThat(html).contains("51–100 από 120", "Πελάτης 051", "Πελάτης 100")
					.doesNotContain("Πελάτης 050", "Πελάτης 101")
					// The links keep the order and go to the other pages.
					.contains("href=\"/customers?page=1&amp;dir=asc\"", "href=\"/customers?page=3&amp;dir=asc\"");
		}

		// Two pages either side of this one, the first and the last, and a gap
		// marked where pages are left out.
		@Test
		void showsTheNeighbouringPagesAndTheEnds() throws Exception {
			manyCustomers(600);

			String middle = html("/customers?page=6");

			assertThat(middle).contains("251–300 από 600",
					"href=\"/customers?page=1&amp;dir=asc\"", "href=\"/customers?page=4&amp;dir=asc\"",
					"href=\"/customers?page=5&amp;dir=asc\"", "href=\"/customers?page=7&amp;dir=asc\"",
					"href=\"/customers?page=8&amp;dir=asc\"", "href=\"/customers?page=12&amp;dir=asc\"")
					.doesNotContain("page=2&amp;", "page=3&amp;", "page=9&amp;", "page=10&amp;", "page=11&amp;");
			// Two gaps above and below the pager, four in all.
			assertThat(middle.split("…", -1)).hasSize(5);

			String first = html("/customers");
			assertThat(first).contains("1–50 από 600", "<span class=\"page-link\">Προηγούμενη</span>",
					"href=\"/customers?page=2&amp;dir=asc\"", "href=\"/customers?page=12&amp;dir=asc\"")
					.doesNotContain("page=0");
			assertThat(html("/customers?page=12")).contains("<span class=\"page-link\">Επόμενη</span>");
		}

		@Test
		void putsAPageNumberOutOfRangeRight() {
			manyCustomers(120);

			assertThat(customerService.list(SortDirection.ASC, 9).page()).isEqualTo(3);
			assertThat(customerService.list(SortDirection.ASC, 9).items()).hasSize(20);
			assertThat(customerService.list(SortDirection.ASC, 0).page()).isEqualTo(1);
			assertThat(customerService.list(SortDirection.ASC, -4).page()).isEqualTo(1);
			// Not an offset too large for the query to take.
			assertThat(customerService.list(SortDirection.ASC, Integer.MAX_VALUE).page()).isEqualTo(3);
		}

		@Test
		void turnsTheOrderRoundFromTheColumnHeading() throws Exception {
			customer("Αλεξίου", "Μαρία");
			customer("Βασιλείου", "Νίκος");

			String ascending = html("/customers");
			assertThat(ascending).contains("Α-Ω", "href=\"/customers?dir=desc\"");
			assertThat(ascending.indexOf("Αλεξίου")).isLessThan(ascending.indexOf("Βασιλείου"));

			String descending = html("/customers?dir=desc");
			assertThat(descending).contains("Ω-Α", "href=\"/customers?dir=asc\"");
			assertThat(descending.indexOf("Βασιλείου")).isLessThan(descending.indexOf("Αλεξίου"));
		}

		@Test
		void readsAnUnknownOrderOrPageAsTheUsualOne() throws Exception {
			customer("Αλεξίου", "Μαρία");
			customer("Βασιλείου", "Νίκος");

			String html = html("/customers?dir=sideways&page=");

			assertThat(html.indexOf("Αλεξίου")).isLessThan(html.indexOf("Βασιλείου"));
			assertThat(html).contains("1–2 από 2");
		}

		@Test
		void linksEachNameToItsCardAndCountsTheVehiclesOwned() throws Exception {
			Customer owner = customer("Αλεξίου", "Μαρία");
			owner.setTaxId("900000080");
			customerRepository.save(owner);
			owns(vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001"), owner, true, null, null);
			owns(vehicle("ΚΜΝ4321", "WVWZZZ1KZAW000002"), owner, true, null, null);
			// Sold, so no longer counted.
			owns(vehicle("ΝΖΑ8812", "WVWZZZ1KZAW000003"), owner, true, null, TODAY.minusYears(1));
			customer("Βασιλείου", null);

			String html = html("/customers");

			assertThat(html).contains("href=\"/customers/" + owner.getId() + "\"", "Αλεξίου Μαρία", "900000080",
					"Βασιλείου");
			assertThat(customerService.list(SortDirection.ASC, 1).items()).extracting(CustomerHit::vehicleCount)
					.containsExactly(2L, 0L);
		}

		@Test
		void saysSoWhenThereAreNoCustomers() throws Exception {
			assertThat(html("/customers")).contains("Δεν υπάρχουν πελάτες.").doesNotContain("<table");
		}

	}

	@Nested
	class Vehicles {

		// By plate_normalized, as everywhere: ΑΒΕ and ABE are one plate, so
		// Greek and Latin spellings of a plate are neighbours, not two blocks.
		@Test
		void sortsByPlateWhateverTheLettersItWasTypedIn() {
			vehicle("ΝΖΑ8812", "WVWZZZ1KZAW000001");
			vehicle("HKN9012", "WVWZZZ1KZAW000002");
			vehicle("ΚΜΝ4321", "WVWZZZ1KZAW000003");
			vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000004");

			assertThat(plates(vehicleService.list(SortDirection.ASC, 1)))
					.containsExactly("ΑΒΕ1234", "HKN9012", "ΚΜΝ4321", "ΝΖΑ8812");
			assertThat(plates(vehicleService.list(SortDirection.DESC, 1)))
					.containsExactly("ΝΖΑ8812", "ΚΜΝ4321", "HKN9012", "ΑΒΕ1234");
		}

		@Test
		void showsOnlyOnePage() throws Exception {
			IntStream.rangeClosed(1, 60).forEach(this::jdbcVehicle);

			PageDto<VehicleHit> second = vehicleService.list(SortDirection.ASC, 2);

			assertThat(vehicleService.list(SortDirection.ASC, 1).items()).hasSize(PAGE_SIZE);
			assertThat(second.items()).hasSize(10);
			assertThat(second.totalItems()).isEqualTo(60);
			assertThat(vehicleService.list(SortDirection.ASC, 7).page()).isEqualTo(2);
			assertThat(html("/vehicles?page=2&dir=desc")).contains("51–60 από 60", "ΑΒΕ0010", "ΑΒΕ0001")
					.doesNotContain("ΑΒΕ0011");
		}

		@Test
		void linksThePlateToTheCardAndNamesTheCurrentPrimaryOwner() throws Exception {
			Vehicle owned = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			Vehicle unowned = vehicle("ΚΜΝ4321", "WVWZZZ1KZAW000002");
			Customer owner = customer("Αλεξίου", "Μαρία");
			Customer coOwner = customer("Βασιλείου", "Νίκος");
			owns(owned, owner, true, null, null);
			owns(owned, coOwner, false, null, null);

			String html = html("/vehicles");

			assertThat(html).contains("href=\"/vehicles/" + owned.getId() + "\"", "Volkswagen Golf",
					"href=\"/customers/" + owner.getId() + "\"", "Αλεξίου Μαρία",
					"href=\"/vehicles/" + unowned.getId() + "\"", "Χωρίς κύριο ιδιοκτήτη")
					.doesNotContain("Βασιλείου");
		}

		@Test
		void turnsTheOrderRoundFromTheColumnHeading() throws Exception {
			vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			vehicle("ΚΜΝ4321", "WVWZZZ1KZAW000002");

			assertThat(html("/vehicles")).contains("href=\"/vehicles?dir=desc\"");
			String descending = html("/vehicles?dir=desc");
			assertThat(descending).contains("href=\"/vehicles?dir=asc\"");
			assertThat(descending.indexOf("ΚΜΝ4321")).isLessThan(descending.indexOf("ΑΒΕ1234"));
		}

		@Test
		void saysSoWhenThereAreNoVehicles() throws Exception {
			assertThat(html("/vehicles")).contains("Δεν υπάρχουν οχήματα.").doesNotContain("<table");
		}

		private void jdbcVehicle(int number) {
			ListPagesTest.this.jdbcVehicle(number);
		}

	}

	@Nested
	class Policies {

		@Test
		void putsTheLatestEndDateFirstAndTurnsRound() throws Exception {
			Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			policy(vehicle, "2100000001", "Northwind", TODAY.minusYears(2), TODAY.minusYears(1));
			policy(vehicle, "2100000003", "Northwind", TODAY.minusMonths(6), TODAY.plusMonths(6));
			policy(vehicle, "2100000002", "Northwind", TODAY.minusYears(1), TODAY);

			assertThat(numbers(policyService.list(null, SortDirection.DESC, 1)))
					.containsExactly("2100000003", "2100000002", "2100000001");
			assertThat(numbers(policyService.list(null, SortDirection.ASC, 1)))
					.containsExactly("2100000001", "2100000002", "2100000003");

			String usual = html("/policies");
			assertThat(usual.indexOf("2100000003")).isLessThan(usual.indexOf("2100000001"));
			assertThat(usual).contains("href=\"/policies?dir=asc\"", "νεότερα πρώτα");
			String turned = html("/policies?dir=asc");
			assertThat(turned.indexOf("2100000001")).isLessThan(turned.indexOf("2100000003"));
			assertThat(turned).contains("href=\"/policies?dir=desc\"", "παλαιότερα πρώτα");
		}

		// The filter is the expiry screen's: an exact company name, all when
		// none is chosen.
		@Test
		void filtersByInsuranceCompanyAndOffersEveryCompany() throws Exception {
			Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			policy(vehicle, "2100000001", "Northwind", TODAY.minusYears(2), TODAY.minusYears(1));
			policy(vehicle, "2100000002", "Acme", TODAY.minusYears(1), TODAY);
			policy(vehicle, "2100000003", "Acme", TODAY, TODAY.plusYears(1));

			PolicyListDto acme = policyService.list("Acme", SortDirection.DESC, 1);

			assertThat(numbers(acme)).containsExactly("2100000003", "2100000002");
			assertThat(acme.policies().totalItems()).isEqualTo(2);
			assertThat(acme.insuranceCompany()).isEqualTo("Acme");
			assertThat(acme.insuranceCompanies()).containsExactly("Acme", "Northwind");
			assertThat(numbers(policyService.list("", SortDirection.DESC, 1))).hasSize(3);
			assertThat(policyService.list(null, SortDirection.DESC, 1).insuranceCompany()).isNull();

			String html = html("/policies?insuranceCompany=Acme");
			assertThat(html).contains("2100000003", "2100000002", "1–2 από 2",
					"<option value=\"Acme\" selected", "<option value=\"Northwind\"")
					.doesNotContain("2100000001");
			assertThat(html("/policies?insuranceCompany=Nobody")).contains("Κανένα συμβόλαιο.");
		}

		@Test
		void pagesKeepTheOrderAndTheFilter() throws Exception {
			Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			IntStream.rangeClosed(1, 60).forEach(i -> policy(vehicle, "21%08d".formatted(i), "Acme",
					TODAY.minusDays(1000L - i), TODAY.minusDays(999L - i)));
			policy(vehicle, "2199999999", "Northwind", TODAY.minusDays(5), TODAY);

			PolicyListDto second = policyService.list("Acme", SortDirection.ASC, 2);
			assertThat(policyService.list("Acme", SortDirection.ASC, 1).policies().items()).hasSize(PAGE_SIZE);
			assertThat(second.policies().items()).hasSize(10);
			assertThat(second.policies().totalItems()).isEqualTo(60);
			assertThat(policyService.list("Acme", SortDirection.ASC, 8).policies().page()).isEqualTo(2);
			// All companies: the 61st policy too.
			assertThat(policyService.list(null, SortDirection.ASC, 2).policies().items()).hasSize(11);

			String html = html("/policies?insuranceCompany=Acme&dir=asc&page=2");
			assertThat(html).contains("51–60 από 60",
					"href=\"/policies?page=1&amp;dir=asc&amp;insuranceCompany=Acme\"")
					.doesNotContain("2199999999");
		}

		@Test
		void showsEachPolicyWithItsStatusPlateAndCustomerAtItsStart() throws Exception {
			Vehicle vehicle = vehicle("ΑΒΕ1234", "WVWZZZ1KZAW000001");
			Customer seller = customer("Αλεξίου", "Μαρία");
			Customer buyer = customer("Βασιλείου", "Νίκος");
			LocalDate transfer = TODAY.minusMonths(8);
			owns(vehicle, seller, true, null, transfer);
			owns(vehicle, buyer, true, transfer, null);
			policy(vehicle, "2100000001", "Acme", TODAY.minusYears(2), TODAY.minusYears(1));
			policy(vehicle, "2100000002", "Acme", TODAY.minusMonths(6), TODAY.plusMonths(6));
			Vehicle unowned = vehicle("ΚΜΝ4321", "WVWZZZ1KZAW000002");
			policy(unowned, "2100000003", "Acme", TODAY.minusMonths(1), TODAY.plusDays(10));

			String html = html("/policies");

			// The customer of the older policy is who held the vehicle then.
			assertThat(row(html, "2100000001")).contains("Ληγμένο", "href=\"/vehicles/" + vehicle.getId() + "\"",
					"ΑΒΕ1234", "href=\"/customers/" + seller.getId() + "\"", "Αλεξίου Μαρία", "Acme", "180,00 €")
					.doesNotContain("Βασιλείου");
			assertThat(row(html, "2100000002")).contains("Ενεργό", "href=\"/customers/" + buyer.getId() + "\"",
					"Βασιλείου Νίκος").doesNotContain("Αλεξίου");
			assertThat(row(html, "2100000003")).contains("Λήγει σύντομα", "ΚΜΝ4321").doesNotContain("/customers/");
		}

		@Test
		void saysSoWhenThereAreNoPolicies() throws Exception {
			assertThat(html("/policies")).contains("Κανένα συμβόλαιο.").doesNotContain("<table");
		}

	}

	@Nested
	class Header {

		@Test
		void linksTheThreeListsFromEveryPage() throws Exception {
			for (String page : List.of("/", "/customers", "/vehicles", "/policies", "/customers/new")) {
				assertThat(html(page)).as(page).contains("href=\"/customers\"", "href=\"/vehicles\"",
						"href=\"/policies\"");
			}
		}

		@Test
		void marksTheListTheClerkIsOn() throws Exception {
			assertThat(html("/customers")).contains("<a class=\"nav-link active\" href=\"/customers\"")
					.doesNotContain("<a class=\"nav-link active\" href=\"/vehicles\"");
			assertThat(html("/vehicles")).contains("<a class=\"nav-link active\" href=\"/vehicles\"");
			assertThat(html("/policies")).contains("<a class=\"nav-link active\" href=\"/policies\"");
		}

	}

	private String html(String url) throws Exception {
		return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	// The table row of one policy, so a name elsewhere on the page does not count.
	private static String row(String html, String policyNumber) {
		int number = html.indexOf(policyNumber);
		assertThat(number).as("policy " + policyNumber).isPositive();
		return html.substring(html.lastIndexOf("<tr", number), html.indexOf("</tr>", number));
	}

	private static List<String> lastNames(PageDto<CustomerHit> page) {
		return page.items().stream().map(CustomerHit::lastName).toList();
	}

	private static List<String> plates(PageDto<VehicleHit> page) {
		return page.items().stream().map(VehicleHit::plate).toList();
	}

	private static List<String> numbers(PolicyListDto list) {
		return list.policies().items().stream().map(PolicyViewDto::policyNumber).toList();
	}

	private Customer customer(String lastName, String firstName) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		return customerRepository.save(customer);
	}

	private void manyCustomers(int count) {
		jdbcTemplate.batchUpdate("INSERT INTO customer (last_name) VALUES (?)",
				IntStream.rangeClosed(1, count).mapToObj(i -> new Object[] { "Πελάτης %03d".formatted(i) }).toList());
	}

	private Vehicle vehicle(String plate, String vin) {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin(vin);
		vehicle.setPlate(plate);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private void jdbcVehicle(int number) {
		String plate = "ΑΒΕ%04d".formatted(number);
		jdbcTemplate.update("""
				INSERT INTO vehicle (vin, plate, plate_normalized, brand, model, first_registration, category,
					usage_type, color, power_kw, fuel_type)
				VALUES (?, ?, ?, 'Volkswagen', 'Golf', DATE '2012-05-14', 'M1', 'ΕΙΧ', 'Λευκό', 81, 'ΒΕΝΖΙΝΗ')
				""", "WVWZZZ1KZAW%06d".formatted(number), plate, TextNormalizationUtils.normalizePlate(plate));
	}

	private void owns(Vehicle vehicle, Customer customer, boolean primary, LocalDate fromDate, LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(primary);
		ownership.setFromDate(fromDate);
		ownership.setToDate(toDate);
		ownershipRepository.save(ownership);
	}

	private void policy(Vehicle vehicle, String policyNumber, String company, LocalDate startDate,
			LocalDate endDate) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany(company);
		policy.setStartDate(startDate);
		policy.setEndDate(endDate);
		policy.setPremium(new BigDecimal("180.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
