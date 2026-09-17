package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

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

	@Test
	void asksForInputWhenTheBoxIsEmpty() throws Exception {
		assertThat(html(get("/search")))
				.contains("Πληκτρολογήστε στο πεδίο αναζήτησης")
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
		// What was typed stays in the box after the search.
		assertThat(html(get("/search").param("q", "αλεξ"))).contains("value=\"αλεξ\"");
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
