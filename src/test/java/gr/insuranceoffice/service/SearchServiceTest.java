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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.stat.QueryStatistics;
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
import gr.insuranceoffice.dto.SearchSuggestionsDto;
import gr.insuranceoffice.dto.SearchSuggestionsDto.Group;
import gr.insuranceoffice.dto.SearchSuggestionsDto.Suggestion;
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
	private CustomerService customerService;

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
		assertThat(result.vehicles()).containsExactly(new VehicleHit(vehicle.getId(), "ΑΒΕ1234", "Volkswagen", "Golf",
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

	// Task 15: the search sorts names as the customer list does, in Greek
	// alphabetical order. Accented capitals sit among their letters, so
	// «Άγγελος» comes after «Αβραμίδης» and before «Αλεξίου» (a code point
	// order puts it before every Α), and Greek names come before Latin ones
	// (the database default would put «Smith» first). All three ways of
	// finding several customers at once sort the same.
	@ParameterizedTest
	@ValueSource(strings = { "example", "6900000001", "2990000000" })
	void sortsCustomersInGreekAlphabeticalOrderAsTheCustomerListDoes(String input) {
		for (String lastName : List.of("Smith", "Αλεξίου", "Βασιλείου", "Άγγελος", "Αβραμίδης", "αθανασίου")) {
			Customer customer = new Customer();
			customer.setLastName(lastName);
			customer.setMobile("6900000001");
			customer.setPhone("2990000000");
			customer.setEmail("office@example.gr");
			customerRepository.save(customer);
		}
		List<String> inGreekOrder = List.of("Αβραμίδης", "Άγγελος", "αθανασίου", "Αλεξίου", "Βασιλείου", "Smith");

		assertThat(searchService.search(input).customers()).extracting(CustomerHit::lastName)
				.containsExactlyElementsOf(inGreekOrder);
		assertThat(customerService.list(SortDirection.ASC, 1).items()).extracting(CustomerHit::lastName)
				.containsExactlyElementsOf(inGreekOrder);
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

	// Task 16f-1: an email is free text, found through search_normalized
	// (V2), whole, in capitals or in part; the search box says so.
	@ParameterizedTest
	@ValueSource(strings = { "maria.alexiou@example.gr", "MARIA.ALEXIOU@EXAMPLE.GR", "alexiou@", "@example.gr" })
	void findsACustomerByEmail(String input) {
		Customer maria = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		maria.setEmail("maria.alexiou@example.gr");
		customerRepository.save(maria);
		Customer nikos = saveCustomer("Βασιλείου", "Νίκος", "900000017");
		nikos.setEmail("nikos@example.com");
		customerRepository.save(nikos);

		SearchResultDto result = searchService.search(input);

		assertThat(result.searchedAs()).containsExactly(TEXT);
		assertThat(result.customers()).extracting(CustomerHit::id).containsExactly(maria.getId());
	}

	// The _ of an email is a letter, not LIKE's "any character".
	@Test
	void matchesTheUnderscoreOfAnEmailLiterally() {
		Customer nikos = saveCustomer("Βασιλείου", "Νίκος", "900000017");
		nikos.setEmail("nikos_v@example.com");
		customerRepository.save(nikos);

		assertThat(searchService.search("nikos_v@example.com").customers()).extracting(CustomerHit::id)
				.containsExactly(nikos.getId());
		assertThat(searchService.search("nikosXv@example.com").customers()).isEmpty();
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

	// Task 21a: the suggestions under the header's box. Their JSON is in
	// SearchControllerTest.

	// Whatever the browser sends: no statement, and not even a transaction,
	// so no connection is taken from the pool.
	@ParameterizedTest(name = "«{0}»")
	@ValueSource(strings = { "", "   ", "α", "αβ", "  αβ  ", "12", "ΑΒ\t" })
	void suggestsNothingBelowThreeCharactersWithoutAskingTheDatabase(String input) {
		saveCustomer("Αβραμίδης", "Αβραάμ", "900000017");

		Statistics statistics = statisticsOf(() -> assertThat(searchService.suggest(input))
				.isEqualTo(new SearchSuggestionsDto(input, List.of(), 0, false, "0")));

		assertThat(statistics.getPrepareStatementCount()).isZero();
		assertThat(statistics.getTransactionCount()).isZero();
		assertThat(statistics.getConnectCount()).isZero();
	}

	// The counters above do count: three characters reach the database.
	@Test
	void asksTheDatabaseFromThreeCharacters() {
		saveCustomer("Αβραμίδης", "Αβραάμ", "900000017");

		Statistics statistics = statisticsOf(() -> assertThat(searchService.suggest(" αβρ ").groups())
				.extracting(Group::label).containsExactly("Πελάτες"));

		assertThat(statistics.getPrepareStatementCount()).isPositive();
		assertThat(statistics.getTransactionCount()).isEqualTo(1);
		assertThat(statistics.getConnectCount()).isEqualTo(1);
	}

	@Test
	void writesEachSuggestionInTheWordsOfTheResultsPage() {
		Customer konstantinos = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		Customer maria = saveCustomer("Αλεξίου", "Μαρία", "900000080");
		// No ΑΦΜ, no first name and no vehicle.
		Customer company = saveCustomer("Αλεξίου ΑΕ", null, null);
		Vehicle astra = saveVehicle("W0L0AHL3555000001", "NZA-8812", "Opel", "Astra J");
		Vehicle corsa = saveVehicle("W0L0XCE7584000001", "ΗΟΚ-2201", "Opel", "Corsa");
		Vehicle unowned = saveVehicle("W0L0XCE7584000002", "ΗΟΚ-2202", "Opel", "Meriva");
		saveOwnership(astra, konstantinos, "50", true);
		saveOwnership(corsa, maria, "100", true);
		Ownership sold = ownership(unowned, maria, "100", true);
		sold.setToDate(LocalDate.of(2025, 1, 31));
		ownershipRepository.save(sold);
		// Maria also co-owns the Astra: 2 vehicles.
		saveOwnership(astra, maria, "50", false);

		// By name, as on the page: «Αλεξίου ΑΕ» before «Αλεξίου Κωνσταντίνος».
		assertThat(searchService.suggest("αλεξ").groups()).singleElement().satisfies(group -> {
			assertThat(group.label()).isEqualTo("Πελάτες");
			assertThat(group.suggestions()).containsExactly(
					new Suggestion("Αλεξίου ΑΕ", "ΑΦΜ — · 0 οχήματα", "/customers/" + company.getId()),
					new Suggestion("Αλεξίου Κωνσταντίνος", "ΑΦΜ 900000017 · 1 όχημα", "/customers/" + konstantinos.getId()),
					new Suggestion("Αλεξίου Μαρία", "ΑΦΜ 900000080 · 2 οχήματα", "/customers/" + maria.getId()));
		});
		// A sold vehicle has no current primary owner: nobody is named.
		assertThat(searchService.suggest("opel").groups()).singleElement().satisfies(group -> {
			assertThat(group.label()).isEqualTo("Οχήματα");
			assertThat(group.suggestions()).containsExactly(
					new Suggestion("ΗΟΚ2201", "Opel Corsa · Αλεξίου Μαρία", "/vehicles/" + corsa.getId()),
					new Suggestion("ΗΟΚ2202", "Opel Meriva", "/vehicles/" + unowned.getId()),
					new Suggestion("NZA8812", "Opel Astra J · Αλεξίου Κωνσταντίνος", "/vehicles/" + astra.getId()));
		});
	}

	// A policy number finds a vehicle, shown with the number, never a group
	// of policies. The same ten digits are a landline too.
	@Test
	void showsTheVehicleAPolicyNumberFoundWithTheNumber() {
		Customer withLandline = saveCustomer("Αλεξίου", "Κωνσταντίνος", "900000017");
		withLandline.setPhone("2100000001");
		customerRepository.save(withLandline);
		Customer owner = saveCustomer("Δημητρίου", "Ελένη", "900000029");
		Vehicle insured = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveOwnership(insured, owner, "100", true);
		savePolicy(insured, "2100000001");

		assertThat(searchService.suggest("2100000001")).isEqualTo(new SearchSuggestionsDto("2100000001", List.of(
				new Group("Πελάτες", List.of(new Suggestion("Αλεξίου Κωνσταντίνος", "ΑΦΜ 900000017 · 0 οχήματα",
						"/customers/" + withLandline.getId()))),
				new Group("Οχήματα", List.of(new Suggestion("ΑΒΕ1234", "συμβόλαιο 2100000001",
						"/vehicles/" + insured.getId())))),
				2, false, "2"));
		// Found another way, the same vehicle shows its model and owner.
		assertThat(searchService.suggest("ΑΒΕ-1234").groups()).singleElement()
				.satisfies(group -> assertThat(group.suggestions()).extracting(Suggestion::detail)
						.containsExactly("Volkswagen Golf · Δημητρίου Ελένη"));
	}

	// However the input is read, the suggestions are the first rows of each
	// group of the results page, and the total is the page's row count.
	@ParameterizedTest(name = "«{0}»")
	@ValueSource(strings = { "αλεξ", "golf", "renault", "example", "ΑΒΕ-1234", "abe1234", "900000017",
			"6900000001", "2100000001", "2990000000", "WVWZZZ1KZAW100001", "ηκν 10", "Ζωγράφου" })
	void suggestsTheFirstRowsOfTheResultsPage(String input) {
		for (int i = 0; i < 12; i++) {
			Customer customer = saveCustomer("Αλεξίου", String.format("Όνομα %02d", i), String.format("9000001%02d", i));
			customer.setMobile("6900000001");
			customer.setEmail(String.format("alexiou%02d@example.gr", i));
			customerRepository.save(customer);
			Vehicle golf = saveVehicle(String.format("WVWZZZ1KZAW1000%02d", i), String.format("ΗΚΝ-10%02d", i),
					"Volkswagen", "Golf");
			saveOwnership(golf, customer, "100", true);
		}
		for (int i = 0; i < 3; i++) {
			Customer customer = saveCustomer("Renault", "Όνομα " + i, null);
			customer.setPhone("2990000000");
			customerRepository.save(customer);
			saveVehicle("VF1RFB0000000000" + i, "ΥΧΒ-200" + i, "Renault", "Clio");
		}
		Customer owner = saveCustomer("Δημητρίου", "Ελένη", "900000017");
		owner.setPhone("2100000001");
		customerRepository.save(owner);
		Vehicle insured = saveVehicle("WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf");
		saveOwnership(insured, owner, "100", true);
		savePolicy(insured, "2100000001");

		SearchResultDto page = searchService.search(input);
		SearchSuggestionsDto suggestions = searchService.suggest(input);

		// The page's groups, in its order; one without rows is left out.
		List<String> labels = new ArrayList<>();
		if (!page.customers().isEmpty()) {
			labels.add("Πελάτες");
		}
		if (!page.vehicles().isEmpty()) {
			labels.add("Οχήματα");
		}
		assertThat(suggestions.groups()).extracting(Group::label).containsExactlyElementsOf(labels);
		List<String> customers = urls(suggestions, "Πελάτες");
		List<String> vehicles = urls(suggestions, "Οχήματα");
		assertThat(customers).containsExactlyElementsOf(page.customers().stream()
				.map(hit -> "/customers/" + hit.id()).limit(customers.size()).toList());
		assertThat(vehicles).containsExactlyElementsOf(page.vehicles().stream()
				.map(hit -> "/vehicles/" + hit.id()).limit(vehicles.size()).toList());
		// As many as there are, up to eight.
		int total = page.customers().size() + page.vehicles().size();
		assertThat(customers.size() + vehicles.size()).isEqualTo(Math.min(total, SearchService.MAX_SUGGESTIONS));
		assertThat(suggestions.total()).isEqualTo(total);
		assertThat(suggestions.truncated()).isEqualTo(page.truncated());
	}

	private static List<String> urls(SearchSuggestionsDto suggestions, String label) {
		return suggestions.groups().stream()
				.filter(group -> group.label().equals(label))
				.flatMap(group -> group.suggestions().stream())
				.map(Suggestion::url)
				.toList();
	}

	@ParameterizedTest(name = "{0} customers, {1} vehicles → {2} + {3}")
	@MethodSource
	void sharesTheEightSuggestionsBetweenTheGroups(int customers, int vehicles, int shownCustomers,
			int shownVehicles) {
		for (int i = 0; i < customers; i++) {
			Customer customer = saveCustomer("Αλεξίου", String.format("Όνομα %02d", i), null);
			customer.setEmail(String.format("c%02d@lancia.example", i));
			customerRepository.save(customer);
		}
		for (int i = 0; i < vehicles; i++) {
			saveVehicle(String.format("ZLA0000000000%04d", i), String.format("LNC-1%03d", i), "Lancia", "Delta");
		}

		SearchSuggestionsDto suggestions = searchService.suggest("lancia");

		assertThat(urls(suggestions, "Πελάτες")).hasSize(shownCustomers);
		assertThat(urls(suggestions, "Οχήματα")).hasSize(shownVehicles);
		assertThat(suggestions.groups()).allSatisfy(group -> assertThat(group.suggestions()).isNotEmpty());
		assertThat(suggestions.total()).isEqualTo(customers + vehicles);
		assertThat(suggestions.totalLabel()).isEqualTo(String.valueOf(customers + vehicles));
	}

	static Stream<Arguments> sharesTheEightSuggestionsBetweenTheGroups() {
		return Stream.of(
				arguments(10, 10, 4, 4),
				arguments(2, 10, 2, 6),
				arguments(10, 1, 7, 1),
				arguments(0, 10, 0, 8),
				arguments(9, 0, 8, 0),
				arguments(3, 3, 3, 3),
				arguments(4, 5, 4, 4),
				arguments(0, 0, 0, 0));
	}

	// The total is what the page shows, 50 per group at most, and «N+» says
	// a group was cut, as «Πάρα πολλά αποτελέσματα» does on the page.
	@Test
	void countsWhatThePageShowsAndMarksACutGroup() {
		for (int i = 0; i <= SearchService.MAX_HITS; i++) {
			saveCustomer("Παπαδόπουλος", String.format("Όνομα %03d", i), null);
		}
		// Found by the plate form of the input: ΠΑΠ is stored as ΠAΠ.
		saveVehicle("WVWZZZ1KZAW123456", "ΠΑΠ-1234", "Volkswagen", "Golf");
		saveVehicle("WVWZZZ1KZAW123457", "ΠΑΠ-1235", "Volkswagen", "Polo");

		SearchSuggestionsDto suggestions = searchService.suggest("ΠΑΠ");

		assertThat(suggestions.groups()).extracting(group -> group.suggestions().size()).containsExactly(6, 2);
		assertThat(suggestions.total()).isEqualTo(SearchService.MAX_HITS + 2);
		assertThat(suggestions.truncated()).isTrue();
		assertThat(suggestions.totalLabel()).isEqualTo((SearchService.MAX_HITS + 2) + "+");
		SearchResultDto page = searchService.search("ΠΑΠ");
		assertThat(page.customers().size() + page.vehicles().size()).isEqualTo(suggestions.total());
		assertThat(page.truncated()).isTrue();
	}

	// The search keeps up to 51 rows per group, but vehicle counts and owners
	// are loaded only for the rows suggested.
	@Test
	void loadsCountsAndOwnersOnlyForTheSuggestedRows() {
		for (int i = 0; i < 30; i++) {
			Customer customer = saveCustomer("Παπαδόπουλος", String.format("Όνομα %02d", i), null);
			Vehicle vehicle = saveVehicle(String.format("WVWZZZ1KZAW1000%02d", i), String.format("ΗΚΝ-10%02d", i),
					"Volkswagen", "Golf");
			saveOwnership(vehicle, customer, "100", true);
		}

		assertThat(ownershipsLoadedBy(() -> searchService.suggest("golf"))).isEqualTo(SearchService.MAX_SUGGESTIONS);
		assertThat(ownershipsLoadedBy(() -> searchService.search("golf"))).isEqualTo(30);
		assertThat(customersCountedBy(() -> searchService.suggest("Παπαδόπουλος")))
				.isEqualTo(SearchService.MAX_SUGGESTIONS);
		assertThat(customersCountedBy(() -> searchService.search("Παπαδόπουλος"))).isEqualTo(30);
	}

	// Ownership rows read to name the vehicles' primary owners.
	private long ownershipsLoadedBy(Runnable search) {
		return statisticsOf(search).getEntityStatistics(Ownership.class.getName()).getLoadCount();
	}

	// Customers whose vehicles were counted: one row each, as every one owns one.
	private long customersCountedBy(Runnable search) {
		Statistics statistics = statisticsOf(search);
		return Arrays.stream(statistics.getQueries())
				.filter(query -> query.contains("count(distinct o.vehicle.id)"))
				.map(statistics::getQueryStatistics)
				.mapToLong(QueryStatistics::getExecutionRowCount)
				.sum();
	}

	private Statistics statisticsOf(Runnable action) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		try {
			action.run();
			return statistics;
		} finally {
			statistics.setStatisticsEnabled(false);
		}
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
