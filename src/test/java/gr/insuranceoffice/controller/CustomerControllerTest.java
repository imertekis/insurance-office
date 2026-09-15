package gr.insuranceoffice.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.repository.CustomerRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

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

	private static Customer customer(String lastName, String firstName, String taxId, String mobile) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		customer.setMobile(mobile);
		return customer;
	}

}
