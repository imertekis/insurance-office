package gr.insuranceoffice.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.repository.CustomerRepository;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@AfterEach
	void deleteCustomers() {
		customerRepository.deleteAll();
	}

	@Test
	void returnsEmptyJsonArrayWhenThereAreNoCustomers() throws Exception {
		mockMvc.perform(get("/api/customers"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void returnsPersistedCustomersSortedByName() throws Exception {
		customerRepository.save(customer("Βασιλείου", "Νίκος", null, null));
		customerRepository.save(customer("Αλεξίου", "Κωνσταντίνος", "123456783", "6900000001"));

		mockMvc.perform(get("/api/customers"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].lastName").value("Αλεξίου"))
				.andExpect(jsonPath("$[0].firstName").value("Κωνσταντίνος"))
				.andExpect(jsonPath("$[0].taxId").value("123456783"))
				.andExpect(jsonPath("$[0].entityType").value("INDIVIDUAL"))
				.andExpect(jsonPath("$[1].lastName").value("Βασιλείου"))
				// tax_id and mobile are nullable (DECISIONS §1, §2).
				.andExpect(jsonPath("$[1].taxId").value(nullValue()))
				.andExpect(jsonPath("$[1].mobile").value(nullValue()));
	}

	@Test
	void createsACustomerWithoutTaxIdAndWarns() throws Exception {
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
				.content(json(dto(null, null, null, null))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", startsWith("/api/customers/")))
				.andExpect(jsonPath("$.customer.version").value(0))
				.andExpect(jsonPath("$.warnings[0]").value("Λείπει το ΑΦΜ του πελάτη."));
	}

	@Test
	void answers422WithTheInvalidFields() throws Exception {
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
				.content(json(dto(null, "900000081", "12345", null))))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.violations[*].field").value(contains("taxId", "mobile")));
	}

	// SPEC §9 and acceptance criterion of Task 7: the second save gets 409.
	@Test
	void answers409WhenSomeoneElseSavedFirst() throws Exception {
		Customer stored = customerRepository.save(customer("Αλεξίου", "Μαρία", "900000080", null));

		mockMvc.perform(put("/api/customers/{id}", stored.getId()).contentType(MediaType.APPLICATION_JSON)
				.content(json(dto(stored.getId(), "900000080", "6900000001", 0L))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customer.version").value(1));

		mockMvc.perform(put("/api/customers/{id}", stored.getId()).contentType(MediaType.APPLICATION_JSON)
				.content(json(dto(stored.getId(), "900000080", "6900000002", 0L))))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Ταυτόχρονη αλλαγή"));

		mockMvc.perform(get("/api/customers"))
				.andExpect(jsonPath("$[0].mobile").value("6900000001"));
	}

	@Test
	void answers404ForACustomerThatNoLongerExists() throws Exception {
		mockMvc.perform(put("/api/customers/{id}", 999).contentType(MediaType.APPLICATION_JSON)
				.content(json(dto(999L, null, null, 0L))))
				.andExpect(status().isNotFound());
	}

	private String json(CustomerDto dto) {
		return jsonMapper.writeValueAsString(dto);
	}

	private static CustomerDto dto(Long id, String taxId, String mobile, Long version) {
		return new CustomerDto(id, taxId, "INDIVIDUAL", "Αλεξίου", "Μαρία", null, null, null, null, null, null, null,
				mobile, null, null, null, version);
	}

	private static Customer customer(String lastName, String firstName, String taxId, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		customer.setMobile(mobile);
		return customer;
	}

}
