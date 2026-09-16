package gr.insuranceoffice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.repository.CustomerRepository;

/**
 * search_normalized is computed by PostgreSQL, while search input is
 * normalized in Java. These tests prove the column is always filled and that
 * both sides agree.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SearchNormalizationConsistencyTest {

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void jpaInsertFillsSearchNormalized() {
		Customer customer = customer("Αλεξίου", "Μαρία");
		customer.setTaxId("123456783");

		Customer saved = customerRepository.saveAndFlush(customer);

		assertThat(saved.getSearchNormalized()).isEqualTo("ΑΛΕΞΙΟΥ ΜΑΡΙΑ 123456783");
		assertThat(searchNormalizedInDatabase(saved.getId())).isEqualTo("ΑΛΕΞΙΟΥ ΜΑΡΙΑ 123456783");
	}

	@Test
	void jpaUpdateRecomputesSearchNormalized() {
		Customer customer = customerRepository.saveAndFlush(customer("Αλεξίου", "Μαρία"));

		customer.setMobile("6900000001");
		customerRepository.saveAndFlush(customer);

		assertThat(customer.getSearchNormalized()).isEqualTo("ΑΛΕΞΙΟΥ ΜΑΡΙΑ 6900000001");
		assertThat(searchNormalizedInDatabase(customer.getId())).isEqualTo("ΑΛΕΞΙΟΥ ΜΑΡΙΑ 6900000001");
	}

	@Test
	void directSqlInsertFillsSearchNormalized() {
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO customer (last_name, first_name, mobile, phone, email)
				VALUES (?, ?, ?, ?, ?)
				RETURNING id
				""", Long.class, "Παπαδόπουλος", "Γιώργος", "6900000002", "2100000000", "g.papadopoulos@example.com");

		String expected = "ΠΑΠΑΔΟΠΟΥΛΟΣ ΓΙΩΡΓΟΣ 6900000002 2100000000 G.PAPADOPOULOS@EXAMPLE.COM";
		assertThat(searchNormalizedInDatabase(id)).isEqualTo(expected);
		assertThat(customerRepository.findById(id)).get()
				.extracting(Customer::getSearchNormalized)
				.isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Αλεξίου", "αλεξιου", "ΑΛΕΞΙΟΥ", "ΑΛΕΞΊΟΥ", "αΛεΞίΟυ",
			"Κωνσταντίνος", "ΚΩΝΣΤΑΝΤΙΝΟΣ", "κωνσταντινος",
			"ΆΈΉΊΌΎΏ άέήίόύώ", "ϊΐϋΰ ΪΫ", "Μαΐου",
			"ἀᾶῥ",
			// Leading and trailing whitespace: SQL rtrim() strips trailing
			// spaces only, so normalizeText() must keep the leading ones.
			"Αλεξίου ", "Αλεξίου   ", " Αλεξίου", "  Μαρία Αλεξίου  ",
			"Müller Café", "maria.alexiou@example.com", "6900000001" })
	void javaNormalizationMatchesDatabase(String input) {
		// The generated column is the source of truth, so compare against it
		// rather than re-typing the SQL expression here.
		String fromDatabase = jdbcTemplate.queryForObject(
				"INSERT INTO customer (last_name) VALUES (?) RETURNING search_normalized", String.class, input);

		assertThat(TextNormalizationUtils.normalizeText(input)).isEqualTo(fromDatabase);
	}

	private String searchNormalizedInDatabase(Long id) {
		return jdbcTemplate.queryForObject("SELECT search_normalized FROM customer WHERE id = ?", String.class, id);
	}

	private static Customer customer(String lastName, String firstName) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		return customer;
	}

}
