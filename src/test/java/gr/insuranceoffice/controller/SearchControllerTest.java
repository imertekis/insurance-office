package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import gr.insuranceoffice.TestcontainersConfiguration;
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

/**
 * The search page: what the clerk gets after typing in the header and
 * pressing Enter. The reading of the input itself is in SearchServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Every page needs a logged-in user from Task 10 on; the login itself is in
// LoginTest.
@WithMockUser
@Import(TestcontainersConfiguration.class)
class SearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

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

	private Customer owner;

	private Vehicle vehicle;

	@BeforeEach
	void startEmpty() {
		truncateTables();
		owner = customer("Αλεξίου", "Κωνσταντίνος", "900000017");
		vehicle = vehicle("ΑΒΕ-1234", "WVWZZZ1KZAW123456");
		owns(vehicle, owner);
		policy(vehicle, "2100000001");
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void showsFreeTextHitsWithTheirTypeLabelAndLinks() throws Exception {
		String html = html(get("/search").param("q", "αλεξ"));

		assertThat(html).contains("Ελεύθερο κείμενο", "ΠΕΛΑΤΗΣ", "Αλεξίου Κωνσταντίνος", "900000017",
				"href=\"/customers/" + owner.getId() + "\"");
	}

	@Test
	void showsAPlateHitAsAVehicleWithItsOwner() throws Exception {
		String html = html(get("/search").param("q", "ΑΒΕ-1234"));

		assertThat(html).contains("Πινακίδα", "ΟΧΗΜΑ", "Volkswagen Golf",
				"href=\"/vehicles/" + vehicle.getId() + "\"",
				// The owner's name is a link too: navigation both ways.
				"href=\"/customers/" + owner.getId() + "\"");
	}

	// CLAUDE.md, resolved conflict 3: ten digits starting with 21 are both.
	@Test
	void labelsAnAmbiguousNumberWithBothTypes() throws Exception {
		String html = html(get("/search").param("q", "2100000001"));

		assertThat(html).contains("Σταθερό", "Αριθμός συμβολαίου", "href=\"/vehicles/" + vehicle.getId() + "\"");
	}

	// Task 16f-1: email among what can be typed; VIN still is too, so the
	// full list keeps it.
	@Test
	void asksForInputWhenTheBoxIsEmpty() throws Exception {
		assertThat(html(get("/search")))
				.contains("Πληκτρολογήστε στο πεδίο αναζήτησης: πινακίδα, ΑΦΜ, όνομα, email, VIN, τηλέφωνο ή αριθμό συμβολαίου.")
				.doesNotContain("Κανένα αποτέλεσμα");
	}

	@Test
	void saysSoWhenNothingMatches() throws Exception {
		assertThat(html(get("/search").param("q", "Παπαδόπουλος"))).contains("Κανένα αποτέλεσμα");
	}

	@Test
	void returnsHtml() throws Exception {
		mockMvc.perform(get("/search").param("q", "αλεξ"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(view().name("search-results"));
	}

	// The box is in the header, so a search starts from wherever the clerk is.
	@Test
	void carriesTheSearchBoxOnEveryPage() throws Exception {
		for (String page : new String[] { "/", "/search", "/vehicles/" + vehicle.getId(),
				"/customers/" + owner.getId() }) {
			assertThat(html(get(page))).as(page)
					.contains("action=\"/search\"", "name=\"q\"", "Αναζήτηση");
		}
		// Task 16f-1: the hint names email where it named VIN.
		assertThat(html(get("/"))).contains("placeholder=\"Πινακίδα, ΑΦΜ, όνομα, email, τηλέφωνο, αρ. συμβολαίου\"");
		// What was typed stays in the box after the search.
		assertThat(html(get("/search").param("q", "αλεξ"))).contains("value=\"αλεξ\"");
	}

	// Task 21a: the suggestions under the header's box, the one answer in
	// JSON, for the page's own script. Which rows they are is in
	// SearchServiceTest.

	// Two customers leave room for six of the ten vehicles.
	@Test
	void suggestsInGroupsWithTextDetailAndAddressAndTheTotal() throws Exception {
		Customer first = lanciaCustomersAndVehicles(2, 10);

		mockMvc.perform(suggestions("lancia"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(jsonPath("$.query").value("lancia"))
				.andExpect(jsonPath("$.groups[*].label").value(contains("Πελάτες", "Οχήματα")))
				.andExpect(jsonPath("$.groups[0].suggestions.length()").value(2))
				.andExpect(jsonPath("$.groups[0].suggestions[0].text").value("Lancia Όνομα 00"))
				.andExpect(jsonPath("$.groups[0].suggestions[0].detail").value("ΑΦΜ — · 0 οχήματα"))
				.andExpect(jsonPath("$.groups[0].suggestions[0].url").value("/customers/" + first.getId()))
				.andExpect(jsonPath("$.groups[1].suggestions.length()").value(6))
				.andExpect(jsonPath("$.groups[1].suggestions[*].text")
						.value(contains("LNC1000", "LNC1001", "LNC1002", "LNC1003", "LNC1004", "LNC1005")))
				.andExpect(jsonPath("$.groups[1].suggestions[0].detail").value("Lancia Delta"))
				.andExpect(jsonPath("$.total").value(12))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.totalLabel").value("12"));
	}

	// The page shows 50 of the 51 customers and says there are more: «60+».
	@Test
	void suggestsFourAndFourAndMarksTheTotalOfACutGroup() throws Exception {
		lanciaCustomersAndVehicles(51, 10);

		mockMvc.perform(suggestions("lancia"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groups[0].suggestions.length()").value(4))
				.andExpect(jsonPath("$.groups[1].suggestions.length()").value(4))
				.andExpect(jsonPath("$.total").value(60))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.totalLabel").value("60+"));
	}

	// The whole answer, key by key: the vehicle a policy number found is a
	// vehicle, with the number; there is no group of policies. The same ten
	// digits are a landline, which finds the customer.
	@Test
	void suggestsThePolicyNumbersVehicleAndTheLandlinesCustomer() throws Exception {
		Customer withLandline = customer("Δημητρίου", "Ελένη", "900000029");
		withLandline.setPhone("2100000001");
		customerRepository.save(withLandline);

		mockMvc.perform(suggestions("2100000001"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "query": "2100000001",
						  "groups": [
						    {
						      "label": "Πελάτες",
						      "suggestions": [
						        { "text": "Δημητρίου Ελένη", "detail": "ΑΦΜ 900000029 · 0 οχήματα", "url": "/customers/%d" }
						      ]
						    },
						    {
						      "label": "Οχήματα",
						      "suggestions": [
						        { "text": "ΑΒΕ1234", "detail": "συμβόλαιο 2100000001", "url": "/vehicles/%d" }
						      ]
						    }
						  ],
						  "total": 2,
						  "truncated": false,
						  "totalLabel": "2"
						}
						""".formatted(withLandline.getId(), vehicle.getId()), JsonCompareMode.STRICT));
	}

	// Whatever the browser sends. That the database is not asked is in
	// SearchServiceTest.
	@ParameterizedTest(name = "«{0}»")
	@ValueSource(strings = { "", "α", "αλ", "  αλ  " })
	void suggestsNothingBelowThreeCharacters(String input) throws Exception {
		mockMvc.perform(suggestions(input))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "query": "%s", "groups": [], "total": 0, "truncated": false, "totalLabel": "0" }
						""".formatted(input), JsonCompareMode.STRICT));
		// Three are enough.
		mockMvc.perform(suggestions("αλε"))
				.andExpect(jsonPath("$.groups[0].suggestions[0].text").value("Αλεξίου Κωνσταντίνος"));
	}

	// As every page: the login page, not JSON. The script sends what a
	// fetch for JSON sends.
	@Test
	@WithAnonymousUser
	void sendsAnAnonymousVisitorToTheLoginPageInsteadOfJson() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(suggestions("αλεξ"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"))
				.andReturn().getResponse();

		assertThat(response.getContentType()).isNull();
		assertThat(response.getContentAsString()).isEmpty();
	}

	// Names are what the clerks typed. The JSON carries them as they are,
	// neither broken nor escaped for HTML: the script shows them as text.
	@Test
	void writesNamesWithMarkupCharactersAsValidJson() throws Exception {
		Customer tricky = customer("<b>Ο'Νιλ</b>", "Σάρα \"Σ\" & Σία", "900000080");
		Vehicle car = vehicle("ΗΚΝ-5678", "VF1RFB00000000001");
		car.setModel("Clio <R.S.> & \"Line\"");
		vehicleRepository.save(car);
		owns(car, tricky);

		MockHttpServletResponse byName = mockMvc.perform(suggestions("900000080"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groups[0].suggestions[0].text").value("<b>Ο'Νιλ</b> Σάρα \"Σ\" & Σία"))
				.andReturn().getResponse();
		MockHttpServletResponse byPlate = mockMvc.perform(suggestions("ΗΚΝ-5678"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groups[0].suggestions[0].detail")
						.value("Volkswagen Clio <R.S.> & \"Line\" · <b>Ο'Νιλ</b> Σάρα \"Σ\" & Σία"))
				.andReturn().getResponse();

		for (MockHttpServletResponse response : new MockHttpServletResponse[] { byName, byPlate }) {
			assertThat(response.getContentAsString(StandardCharsets.UTF_8))
					.contains("<b>Ο'Νιλ</b>", "\\\"Σ\\\" & Σία")
					.doesNotContain("&lt;", "&gt;", "&amp;", "&quot;", "&#");
		}
	}

	private static MockHttpServletRequestBuilder suggestions(String input) {
		return get("/search/suggestions").param("q", input).accept(MediaType.APPLICATION_JSON);
	}

	// Customers and vehicles that «lancia» finds: the customers by their
	// email, the vehicles by their brand. Returns the first customer by name.
	private Customer lanciaCustomersAndVehicles(int customers, int vehicles) {
		Customer first = null;
		for (int i = 0; i < customers; i++) {
			Customer customer = customer("Lancia", String.format("Όνομα %02d", i), null);
			customer.setEmail(String.format("c%02d@lancia.example", i));
			customerRepository.save(customer);
			first = first == null ? customer : first;
		}
		for (int i = 0; i < vehicles; i++) {
			Vehicle lancia = vehicle(String.format("LNC-1%03d", i), String.format("ZLA0000000000%04d", i));
			lancia.setBrand("Lancia");
			lancia.setModel("Delta");
			vehicleRepository.save(lancia);
		}
		return first;
	}

	private String html(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer(String lastName, String firstName, String taxId) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		return customerRepository.save(customer);
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
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		return vehicleRepository.save(vehicle);
	}

	private void owns(Vehicle vehicle, Customer customer) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownershipRepository.save(ownership);
	}

	private void policy(Vehicle vehicle, String policyNumber) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(LocalDate.now().minusMonths(6));
		policy.setEndDate(LocalDate.now().plusMonths(6));
		policy.setPremium(new BigDecimal("180.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
