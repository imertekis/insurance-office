package gr.insuranceoffice.service;

import static gr.insuranceoffice.dto.SearchResultDto.SearchType.MOBILE;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.PHONE;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.PLATE;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.POLICY_NUMBER;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.TAX_ID;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.TEXT;
import static gr.insuranceoffice.dto.SearchResultDto.SearchType.VIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.SearchResultDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.SearchType;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
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
 * Searches PostgreSQL. Not {@code @Transactional}: the data is committed
 * first, so the search runs in a fresh persistence context, where every lazy
 * load it caused would show up as an extra statement. The SQL itself is
 * recorded by {@link RecordingStatementInspector}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
		+ "gr.insuranceoffice.service.SearchServiceTest$RecordingStatementInspector")
@Import(TestcontainersConfiguration.class)
class SearchServiceTest {

	@Autowired
	private SearchService searchService;

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void startEmpty() {
		truncateTables();
		RecordingStatementInspector.STATEMENTS.clear();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@ParameterizedTest(name = "«{0}» → {1}")
	@MethodSource
	void readsTheInputByItsShape(String input, List<SearchType> expected) {
		assertThat(searchService.search(input).searchedAs()).isEqualTo(expected);
	}

	static Stream<Arguments> readsTheInputByItsShape() {
		return Stream.of(
				arguments("WVWZZZ1KZAW123456", List.of(VIN)),
				arguments("wvwzzz1kzaw123456", List.of(VIN)),
				// I, O and Q never appear in a VIN.
				arguments("WVWZZZ1KZIW123456", List.of(TEXT)),
				arguments("WVWZZZ1KZOW123456", List.of(TEXT)),
				arguments("WVWZZZ1KZQW123456", List.of(TEXT)),
				// 17 letters, but a surname: ^.{17}$ would take it for a VIN.
				arguments("Κωνσταντινόπουλος", List.of(TEXT)),
				arguments("ΑΒΕ-1234", List.of(PLATE)),
				arguments("ABE1234", List.of(PLATE)),
				arguments("αβε 1234", List.of(PLATE)),
				arguments("900000017", List.of(TAX_ID)),
				arguments("  900000017 ", List.of(TAX_ID)),
				arguments("6900000001", List.of(MOBILE)),
				arguments("2990000000", List.of(PHONE)),
				// Both a landline and a policy number (CLAUDE.md, resolved conflict 3).
				arguments("2100000001", List.of(PHONE, POLICY_NUMBER)),
				arguments("Αλεξίου", List.of(TEXT)),
				arguments("Volkswagen Golf", List.of(TEXT)),
				arguments("90000001", List.of(TEXT)),
				arguments("69000000011", List.of(TEXT)),
				arguments("1234567890", List.of(TEXT)),
				arguments("ΑΒΕ-12", List.of(TEXT)));
	}

	@Test
	void findsNothingForABlankInput() {
		SearchResultDto result = searchService.search("   ");

		assertThat(result.searchedAs()).isEmpty();
		assertThat(result.customers()).isEmpty();
		assertThat(result.vehicles()).isEmpty();
	}

	@Test
	void looksUpAVinExactlyAndNothingElse() {
		Customer owner = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Vehicle vehicle = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveOwnership(vehicle, owner, "100", true);
		// Contains the same 17 characters, so a text search would find it too.
		saveVehicle("WVWZZZ1KZAW654321", "ΑΒΕ-5678", "Volkswagen", "Golf WVWZZZ1KZAW123456");

		SearchResultDto result = searchService.search("wvwzzz1kzaw123456");

		assertThat(result.searchedAs()).containsExactly(VIN);
		assertThat(result.customers()).isEmpty();
		assertThat(result.vehicles()).containsExactly(new VehicleHit(vehicle.getId(), "ΑΒΕ-1234", "Volkswagen", "Golf",
				owner.getId(), "Αλεξίου", "Κωνσταντίνος"));
	}

	@Test
	void looksUpATaxIdExactlyAndNothingElse() {
		Customer customer = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		// Their mobile contains the same nine digits, so a text search would find them too.
		Customer other = saveCustomer("Δημητρίου", "Ελένη", "900000029");
		other.setMobile("6900000017");
		customerRepository.save(other);

		SearchResultDto result = searchService.search("900000017");

		assertThat(result.searchedAs()).containsExactly(TAX_ID);
		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(customer.getId());
		assertThat(result.vehicles()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ΑΒΕ-1234", "ABE-1234", "abe 1234", "ΑΒΕ1234" })
	void findsTheSameVehicleByAGreekOrLatinPlate(String plate) {
		Vehicle vehicle = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");

		SearchResultDto result = searchService.search(plate);

		assertThat(result.searchedAs()).containsExactly(PLATE);
		assertThat(result.vehicles()).extracting(VehicleHit::id).containsExactly(vehicle.getId());
	}

	@Test
	void findsEveryCustomerWithAMobile() {
		Customer father = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Customer daughter = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		saveCustomer("Δημητρίου", "Ελένη", "900000029");
		for (Customer customer : List.of(daughter, father)) {
			customer.setMobile("6900000001");
			customerRepository.save(customer);
		}

		SearchResultDto result = searchService.search("6900000001");

		assertThat(result.searchedAs()).containsExactly(MOBILE);
		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(father.getId(), daughter.getId());
	}

	@Test
	void searchesTenDigitsStartingWith21AsLandlineAndPolicyNumberAndGroupsTheResults() {
		Customer withLandline = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		withLandline.setPhone("2100000001");
		customerRepository.save(withLandline);
		Customer owner = saveCustomer("Δημητρίου", "Ελένη", "900000029");
		Vehicle insured = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveOwnership(insured, owner, "100", true);
		savePolicy(insured, "2100000001");

		SearchResultDto result = searchService.search("2100000001");

		assertThat(result.searchedAs()).containsExactly(PHONE, POLICY_NUMBER);
		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(withLandline.getId());
		assertThat(result.vehicles()).extracting(VehicleHit::id, VehicleHit::primaryOwnerId)
				.containsExactly(tuple(insured.getId(), owner.getId()));
	}

	@Test
	void searchesALandlineNotStartingWith21OnlyAsALandline() {
		Customer customer = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		customer.setPhone("2990000000");
		customerRepository.save(customer);

		SearchResultDto result = searchService.search("2990000000");

		assertThat(result.searchedAs()).containsExactly(PHONE);
		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(customer.getId());
	}

	// SPEC §14, acceptance criterion 2.
	@ParameterizedTest
	@ValueSource(strings = { "ΑΛΕΞ", "αλεξ", "Αλεξίου", "ΑΛΕΞΙΟΥ", "αλεξιού " })
	void findsCustomersByPartOfTheirNameIgnoringAccentsAndCase(String input) {
		Customer konstantinos = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Customer maria = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		saveCustomer("Δημητρίου", "Ελένη", "900000029");

		SearchResultDto result = searchService.search(input);

		assertThat(result.searchedAs()).containsExactly(TEXT);
		assertThat(result.customers()).extracting(CustomerHit::id)
				.containsExactly(konstantinos.getId(), maria.getId());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Μαρία Αλεξίου", "αλεξ  μαρ" })
	void requiresEveryWordInAnyOrder(String input) {
		saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Customer maria = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		saveCustomer("Δημητρίου", "Μαρία", "900000029");

		assertThat(searchService.search(input).customers()).extracting(CustomerHit::id)
				.containsExactly(maria.getId());
	}

	@Test
	void findsVehiclesByBrandOrModel() {
		Vehicle golf = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveVehicle("VF1RFB00000000001", "ΗΚΝ-5678", "Renault", "Clio");

		assertThat(searchService.search("golf").vehicles()).extracting(VehicleHit::id).containsExactly(golf.getId());
		assertThat(searchService.search("VOLKSWAGEN golf").vehicles()).extracting(VehicleHit::id)
				.containsExactly(golf.getId());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ΑΒΕ", "αβε-12", "ABE 12", "1234" })
	void findsAVehicleByPartOfItsPlateTypedInGreekOrLatin(String input) {
		Vehicle vehicle = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveVehicle("VF1RFB00000000001", "ΗΚΝ-5678", "Renault", "Clio");

		SearchResultDto result = searchService.search(input);

		assertThat(result.searchedAs()).containsExactly(TEXT);
		assertThat(result.vehicles()).extracting(VehicleHit::id).containsExactly(vehicle.getId());
	}

	@ParameterizedTest
	@ValueSource(strings = { "%", "_", "\\", "Αλεξ%ου" })
	void matchesLikeWildcardsLiterally(String input) {
		saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");

		SearchResultDto result = searchService.search(input);

		assertThat(result.customers()).isEmpty();
		assertThat(result.vehicles()).isEmpty();
	}

	@Test
	void countsOnlyTheVehiclesACustomerOwnsNow() {
		Customer customer = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Customer coOwner = saveCustomer("Δημητρίου", "Ελένη", "900000029");
		Vehicle owned = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		Vehicle coOwned = saveVehicle("VF1RFB00000000001", "ΗΚΝ-5678", "Renault", "Clio");
		Vehicle sold = saveVehicle("VF1RFB00000000002", "ΗΚΝ-9012", "Renault", "Megane");
		saveOwnership(owned, customer, "100", true);
		saveOwnership(coOwned, coOwner, "50", true);
		saveOwnership(coOwned, customer, "50", false);
		Ownership former = ownership(sold, customer, "100", true);
		former.setToDate(LocalDate.of(2025, 1, 31));
		ownershipRepository.save(former);

		assertThat(searchService.search("Αλεξίου").customers()).extracting(CustomerHit::vehicleCount)
				.containsExactly(2L);
		// The co-owner is not the primary owner, so the vehicle shows Δημητρίου.
		assertThat(searchService.search("Clio").vehicles()).extracting(VehicleHit::primaryOwnerLastName)
				.containsExactly("Δημητρίου");
		// A former owner is not shown as the owner.
		assertThat(searchService.search("Megane").vehicles()).singleElement()
				.satisfies(hit -> assertThat(hit.primaryOwnerId()).isNull());
	}

	@Test
	void showsTheFirstHitsAndSaysThereAreMore() {
		for (int i = 0; i <= SearchService.MAX_HITS; i++) {
			saveCustomer("Παπαδόπουλος", String.format("Όνομα %03d", i), null);
		}

		SearchResultDto result = searchService.search("Παπαδόπουλος");

		assertThat(result.customers()).hasSize(SearchService.MAX_HITS);
		assertThat(result.customers().getLast().firstName()).isEqualTo("Όνομα 049");
		assertThat(result.truncated()).isTrue();
		assertThat(searchService.search("Όνομα 050").truncated()).isFalse();
	}

	@Test
	void runsAsManyStatementsForManyHitsAsForOne() {
		Customer single = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		saveOwnership(saveVehicle("VF1RFB00000000001", "ΗΚΝ-5678", "Renault", "Clio"), single, "100", true);
		for (int i = 0; i < 5; i++) {
			Customer customer = saveCustomer("Παπαδόπουλος", "Όνομα " + i, null);
			for (int j = 0; j < 2; j++) {
				Vehicle vehicle = saveVehicle("WVWZZZ1KZAW1000" + i + j, "TST-10" + i + j, "Volkswagen", "Golf");
				saveOwnership(vehicle, customer, "100", true);
			}
		}

		// The customer group: vehicle counts for 5 customers against 1.
		assertThat(searchService.search("Παπαδόπουλος").customers()).extracting(CustomerHit::vehicleCount)
				.containsExactly(2L, 2L, 2L, 2L, 2L);
		long forOneCustomer = statementsFor("Αλεξίου");
		assertThat(forOneCustomer).isPositive();
		assertThat(statementsFor("Παπαδόπουλος")).isEqualTo(forOneCustomer);

		// The vehicle group: primary owners for 10 vehicles against 1.
		assertThat(searchService.search("Golf").vehicles()).hasSize(10)
				.allSatisfy(hit -> assertThat(hit.primaryOwnerLastName()).isEqualTo("Παπαδόπουλος"));
		long forOneVehicle = statementsFor("Clio");
		assertThat(forOneVehicle).isPositive();
		assertThat(statementsFor("Golf")).isEqualTo(forOneVehicle);
	}

	// REVIEW-03 §3: every word is a LIKE condition, so their number is capped.
	@Test
	void matchesAtMostSixWordsAndIgnoresTheRest() {
		Customer customer = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		// 60 words. The first six all match; so would the rest, but they are
		// not even sent.
		String sixty = "Αλεξίου Μαρία ".repeat(30);

		SearchResultDto result = searchService.search(sixty);

		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(customer.getId());
		assertThat(likeConditions("from customer")).isEqualTo(SearchService.MAX_TERMS);
		// Vehicles also try each word in plate form (Greek look-alikes to
		// Latin), so at most two conditions per word.
		assertThat(likeConditions("from vehicle")).isEqualTo(2 * SearchService.MAX_TERMS);
	}

	@Test
	void ignoresWordsBeyondTheSixthEvenWhenTheyWouldMatchNothing() {
		Customer customer = saveCustomer("Αλεξίου", "Μαρία", "900000080");

		SearchResultDto result = searchService.search("Αλεξίου Μαρία Αλεξίου Μαρία Αλεξίου Μαρία Ζωγράφου Ξάνθη");

		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(customer.getId());
		assertThat(likeConditions("from customer")).isEqualTo(6);
	}

	@Test
	void readsOnlyTheFirstTwoHundredCharactersOfAVeryLongInput() {
		Customer customer = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		// "Ζωγράφου" starts after character 200. Read, it would match nothing.
		String padded = "Αλεξίου" + " ".repeat(SearchService.MAX_INPUT_LENGTH) + "Ζωγράφου";

		SearchResultDto result = searchService.search(padded);

		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(customer.getId());
		assertThat(likeConditions("from customer")).isEqualTo(1);
	}

	@Test
	void boundsAPastedWallOfText() {
		saveCustomer("Αλεξίου", "Μαρία", "900000080");
		String wall = "ΑΒΓΔ ΕΖΗΘ ".repeat(10_000);

		SearchResultDto result = searchService.search(wall);

		assertThat(result.searchedAs()).containsExactly(TEXT);
		assertThat(result.customers()).isEmpty();
		assertThat(likeConditions("from customer")).isLessThanOrEqualTo(SearchService.MAX_TERMS);
		assertThat(likeConditions("from vehicle")).isLessThanOrEqualTo(2 * SearchService.MAX_TERMS);
	}

	@Test
	void leavesTwoAndThreeWordSearchesAsTheyWere() {
		Customer maria = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		saveCustomer("Αλεξίου", "Γιώργος", "900000091");
		Vehicle golf = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveOwnership(golf, maria, "100", true);

		assertThat(searchService.search("μαρία αλεξίου").customers()).extracting(CustomerHit::id)
				.containsExactly(maria.getId());
		assertThat(likeConditions("from customer")).isEqualTo(2);

		assertThat(searchService.search("Volkswagen Golf ABE").vehicles()).extracting(VehicleHit::id)
				.containsExactly(golf.getId());
		assertThat(likeConditions("from vehicle")).isEqualTo(3);
	}

	// LIKE conditions in the last search's statement against this table.
	private static long likeConditions(String fromTable) {
		List<String> statements = RecordingStatementInspector.STATEMENTS.stream()
				.filter(sql -> sql.toLowerCase(Locale.ROOT).contains(fromTable + " "))
				.toList();
		assertThat(statements).as("statements %s", fromTable).isNotEmpty();
		return LIKE.matcher(statements.getLast().toLowerCase(Locale.ROOT)).results().count();
	}

	private static final Pattern LIKE = Pattern.compile("\\blike\\b");

	/** Keeps the SQL Hibernate sends, cleared before each test. */
	public static class RecordingStatementInspector implements StatementInspector {

		static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

		@Override
		public String inspect(String sql) {
			STATEMENTS.add(sql);
			return sql;
		}

	}

	private long statementsFor(String query) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		try {
			searchService.search(query);
			return statistics.getPrepareStatementCount();
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private Customer saveCustomer(String lastName, String firstName, String taxId) {
		Customer customer = new Customer();
		customer.setLastName(lastName);
		customer.setFirstName(firstName);
		customer.setTaxId(taxId);
		return customerRepository.save(customer);
	}

	private Vehicle saveVehicle(String vin, String plate, String brand, String model) {
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

	private void saveOwnership(Vehicle vehicle, Customer customer, String percentage, boolean primary) {
		ownershipRepository.save(ownership(vehicle, customer, percentage, primary));
	}

	private static Ownership ownership(Vehicle vehicle, Customer customer, String percentage, boolean primary) {
		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customer);
		ownership.setPercentage(new BigDecimal(percentage));
		ownership.setPrimary(primary);
		return ownership;
	}

	private void savePolicy(Vehicle vehicle, String policyNumber) {
		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber(policyNumber);
		policy.setInsuranceCompany("Δοκιμαστική Ασφαλιστική");
		policy.setStartDate(LocalDate.of(2026, 3, 1));
		policy.setEndDate(LocalDate.of(2027, 3, 1));
		policy.setPremium(new BigDecimal("150.00"));
		policyRepository.save(policy);
	}

	private void truncateTables() {
		jdbcTemplate.execute("TRUNCATE ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
