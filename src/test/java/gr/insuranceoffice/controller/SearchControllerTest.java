package gr.insuranceoffice.controller;

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
class SearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

	@AfterEach
	void deleteCustomers() {
		customerRepository.deleteAll();
	}

	@Test
	void returnsTheGroupedResultsAsJson() throws Exception {
		Customer customer = new Customer();
		customer.setLastName("Αλεξίου");
		customer.setFirstName("Κωνσταντίνος");
		customer.setTaxId("900000017");
		customerRepository.save(customer);

		mockMvc.perform(get("/api/search").param("q", "αλεξ"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.query").value("αλεξ"))
				.andExpect(jsonPath("$.searchedAs[0]").value("TEXT"))
				.andExpect(jsonPath("$.customers.length()").value(1))
				.andExpect(jsonPath("$.customers[0].lastName").value("Αλεξίου"))
				.andExpect(jsonPath("$.customers[0].taxId").value("900000017"))
				.andExpect(jsonPath("$.customers[0].vehicleCount").value(0))
				.andExpect(jsonPath("$.vehicles").isEmpty())
				.andExpect(jsonPath("$.truncated").value(false));
	}

	@Test
	void findsNothingWithoutAQuery() throws Exception {
		mockMvc.perform(get("/api/search"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.searchedAs").isEmpty())
				.andExpect(jsonPath("$.customers").isEmpty())
				.andExpect(jsonPath("$.vehicles").isEmpty());
	}

}
