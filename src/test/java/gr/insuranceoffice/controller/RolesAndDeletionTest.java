package gr.insuranceoffice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
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
 * Task 11e: SPEC §2 gives deleting to the ΔΙΑΧΕΙΡΙΣΤΗΣ alone. A vehicle with
 * its current owner, a former owner and one policy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RolesAndDeletionTest {

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

	private Customer maria;

	private Customer george;

	private Vehicle vehicle;

	private Ownership current;

	private Ownership former;

	private Policy policy;

	@BeforeEach
	void aVehicleWithHistory() {
		truncateTables();
		maria = customer("Αλεξίου", "Μαρία");
		george = customer("Γεωργίου", "Γιώργος");
		vehicle = vehicle();
		current = owns(maria, null);
		// Sold his share a year ago; only history now.
		former = owns(george, LocalDate.now().minusYears(1));
		policy = policy();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Nested
	@WithMockUser(roles = "ΥΠΑΛΛΗΛΟΣ")
	class Clerk {

		@Test
		void seesNoDeleteButtonsButCanStillEdit() throws Exception {
			String vehicleCard = html(get("/vehicles/{id}", vehicle.getId()));
			String customerCard = html(get("/customers/{id}", george.getId()));

			assertThat(vehicleCard).contains("/vehicles/" + vehicle.getId() + "/edit", "/policies/" + policy.getId()
					+ "/edit", "/vehicles/" + vehicle.getId() + "/owners")
					.doesNotContain("/delete", "Διαγραφή");
			assertThat(customerCard).contains("/customers/" + george.getId() + "/edit")
					.doesNotContain("/delete", "Διαγραφή");
		}

		@Test
		void canStillCreateAndEditRecords() throws Exception {
			mockMvc.perform(post("/customers").with(csrf()).param("lastName", "Βασιλείου")
					.param("entityType", "INDIVIDUAL"))
					.andExpect(status().is3xxRedirection());
			mockMvc.perform(post("/customers/{id}", maria.getId()).with(csrf())
					.param("id", maria.getId().toString()).param("version", "0")
					.param("lastName", "Αλεξίου").param("firstName", "Μαρία").param("entityType", "INDIVIDUAL")
					// She answers for an insured vehicle, so the mobile stays (DECISIONS §1).
					.param("mobile", "6900000001").param("city", "Δοκιμοχώρι"))
					.andExpect(redirectedUrl("/customers/" + maria.getId()));
		}

		// A direct call gets 403 and the "not allowed" page, not the login.
		@Test
		void isRefusedEveryDeleteEvenByDirectCall() throws Exception {
			for (String url : deleteUrls()) {
				mockMvc.perform(get(url))
						.andExpect(status().isForbidden())
						.andExpect(forwardedUrl("/access-denied"));
				mockMvc.perform(post(url).with(csrf()))
						.andExpect(status().isForbidden())
						.andExpect(forwardedUrl("/access-denied"));
			}

			assertThat(customerRepository.count()).isEqualTo(2);
			assertThat(vehicleRepository.count()).isEqualTo(1);
			assertThat(ownershipRepository.count()).isEqualTo(2);
			assertThat(policyRepository.count()).isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'DELETE'",
					Long.class)).isZero();
		}

		@Test
		void seesAPageExplainingWhy() throws Exception {
			assertThat(html(get("/access-denied"))).contains("Δεν επιτρέπεται", "μόνο στον διαχειριστή");
		}

	}

	@Nested
	@WithMockUser(roles = "ΔΙΑΧΕΙΡΙΣΤΗΣ")
	class Administrator {

		@Test
		void seesDeleteButtonsExceptForACurrentOwner() throws Exception {
			String vehicleCard = html(get("/vehicles/{id}", vehicle.getId()));

			assertThat(vehicleCard).contains(
					"/vehicles/" + vehicle.getId() + "/delete",
					"/policies/" + policy.getId() + "/delete",
					"/ownerships/" + former.getId() + "/delete")
					// Current owners change through the owners form (Task 11e decision).
					.doesNotContain("/ownerships/" + current.getId() + "/delete");
			assertThat(html(get("/customers/{id}", george.getId())))
					.contains("/customers/" + george.getId() + "/delete");
		}

		@Test
		void deletesAVehicleWithItsPoliciesAndOwnersAfterConfirming() throws Exception {
			assertThat(html(get("/vehicles/{id}/delete", vehicle.getId())))
					.contains("Το όχημα ΑΒΕ1234 (Volkswagen Golf)", "Διαγράφονται μαζί:",
							"Το συμβόλαιο 2100000001", "Η ιδιοκτησία του Αλεξίου Μαρία (100%)",
							"Η παλιά ιδιοκτησία του Γεωργίου Γιώργος", "Οριστική διαγραφή");

			mockMvc.perform(post("/vehicles/{id}/delete", vehicle.getId()).with(csrf()))
					.andExpect(redirectedUrl("/"))
					.andExpect(flash().attributeExists("notice"));

			assertThat(vehicleRepository.count()).isZero();
			assertThat(policyRepository.count()).isZero();
			assertThat(ownershipRepository.count()).isZero();
			// DECISIONS §4: all of it can be put back from the log (Task 6).
			assertThat(deleted()).containsExactlyInAnyOrder("Vehicle", "Policy", "Ownership", "Ownership");
			// The customers are not the vehicle's to delete.
			assertThat(customerRepository.count()).isEqualTo(2);
		}

		@Test
		void deletesAPolicyAndReturnsToTheCard() throws Exception {
			assertThat(html(get("/policies/{id}/delete", policy.getId())))
					.contains("Το συμβόλαιο 2100000001 του οχήματος ΑΒΕ1234");

			mockMvc.perform(post("/policies/{id}/delete", policy.getId()).with(csrf()))
					.andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));

			assertThat(policyRepository.count()).isZero();
			assertThat(deleted()).containsExactly("Policy");
		}

		// Task 11e decision: only a closed row, to correct history.
		@Test
		void deletesAFormerOwnershipButNotACurrentOne() throws Exception {
			mockMvc.perform(post("/ownerships/{id}/delete", former.getId()).with(csrf()))
					.andExpect(redirectedUrl("/vehicles/" + vehicle.getId()));
			assertThat(ownershipRepository.findById(former.getId())).isEmpty();

			assertThat(html(get("/ownerships/{id}/delete", current.getId())))
					.contains("Είναι τρέχουσα ιδιοκτησία")
					.doesNotContain("Οριστική διαγραφή");
			assertThat(html(post("/ownerships/{id}/delete", current.getId()).with(csrf())))
					.contains("Είναι τρέχουσα ιδιοκτησία");
			assertThat(ownershipRepository.findById(current.getId())).isPresent();
			assertThat(deleted()).containsExactly("Ownership");
		}

		// Task 11e decision: no vehicle may be left below 100% or without a primary.
		@Test
		void refusesToDeleteACustomerWhoStillOwnsAVehicle() throws Exception {
			assertThat(html(get("/customers/{id}/delete", maria.getId())))
					.contains("Ο πελάτης Αλεξίου Μαρία", "Είναι τρέχων ιδιοκτήτης του οχήματος ΑΒΕ1234")
					.doesNotContain("Οριστική διαγραφή");

			assertThat(html(post("/customers/{id}/delete", maria.getId()).with(csrf())))
					.contains("Είναι τρέχων ιδιοκτήτης του οχήματος ΑΒΕ1234");
			assertThat(customerRepository.findById(maria.getId())).isPresent();
			assertThat(deleted()).isEmpty();
		}

		@Test
		void deletesACustomerWithOnlyFormerOwnerships() throws Exception {
			assertThat(html(get("/customers/{id}/delete", george.getId())))
					.contains("Ο πελάτης Γεωργίου Γιώργος", "Η παλιά ιδιοκτησία του οχήματος ΑΒΕ1234");

			mockMvc.perform(post("/customers/{id}/delete", george.getId()).with(csrf()))
					.andExpect(redirectedUrl("/"));

			assertThat(customerRepository.findById(george.getId())).isEmpty();
			assertThat(ownershipRepository.findById(former.getId())).isEmpty();
			assertThat(deleted()).containsExactlyInAnyOrder("Customer", "Ownership");
			// The vehicle and its current owner are untouched.
			assertThat(ownershipRepository.findById(current.getId())).isPresent();
		}

		@Test
		void answers404ForARecordThatIsGone() throws Exception {
			mockMvc.perform(get("/customers/{id}/delete", 999)).andExpect(status().isNotFound());
			mockMvc.perform(get("/policies/{id}/delete", 999)).andExpect(status().isNotFound());
		}

	}

	// Without a session, a delete is sent to log in, like any other page.
	@Test
	@WithAnonymousUser
	void sendsAnAnonymousVisitorToTheLoginPage() throws Exception {
		mockMvc.perform(post("/vehicles/{id}/delete", vehicle.getId()).with(csrf()))
				.andExpect(redirectedUrl("/login"));
		assertThat(vehicleRepository.count()).isEqualTo(1);
	}

	private List<String> deleteUrls() {
		return List.of("/customers/" + george.getId() + "/delete", "/vehicles/" + vehicle.getId() + "/delete",
				"/policies/" + policy.getId() + "/delete", "/ownerships/" + former.getId() + "/delete");
	}

	private List<String> deleted() {
		return jdbcTemplate.queryForList("SELECT entity_type FROM audit_log WHERE action = 'DELETE'", String.class);
	}

	private String html(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private Customer customer(String lastName, String firstName) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setMobile("6900000001");
		return customerRepository.save(customer);
	}

	private Vehicle vehicle() {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW123456");
		vehicle.setPlate("ΑΒΕ-1234");
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

	private Ownership owns(Customer customer, LocalDate toDate) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(true);
		ownership.setToDate(toDate);
		return ownershipRepository.save(ownership);
	}

	private Policy policy() {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("2100000001");
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(LocalDate.now().minusMonths(6));
		policy.setEndDate(LocalDate.now().plusMonths(6));
		policy.setPremium(new BigDecimal("180.00"));
		return policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
