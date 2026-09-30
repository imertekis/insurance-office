package gr.insuranceoffice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import gr.insuranceoffice.dto.SearchResultDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.SearchType;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.dto.SearchSuggestionsDto;
import gr.insuranceoffice.dto.SearchSuggestionsDto.Group;
import gr.insuranceoffice.dto.SearchSuggestionsDto.Suggestion;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * The single search box (SPEC §6). The input's shape decides what it is: a
 * VIN, plate, ΑΦΜ, mobile, landline or policy number is looked up exactly,
 * and anything else is free text matched against {@code search_normalized} through its trigram
 * index. There is no type dropdown (DECISIONS §3).
 * <p>
 * A fixed number of queries runs whatever the number of hits: vehicle counts
 * and primary owners are loaded for all hits at once.
 * <p>
 * The same search feeds the suggestions under the header's box (Task 21a):
 * the first rows of the page, found the same way.
 */
@Service
public class SearchService {

	/** Hits per group. A broader query should be narrowed, not scrolled. */
	static final int MAX_HITS = 50;

	/**
	 * Characters of input read; the rest is ignored. Far longer than any
	 * name, plate or number a clerk types, and it bounds the work done on
	 * pasted text before it is split (REVIEW-03 §3).
	 */
	static final int MAX_INPUT_LENGTH = 200;

	/**
	 * Words of free text matched; the rest are ignored. Each word is one LIKE
	 * condition, so an unbounded count would let one input build a query
	 * PostgreSQL struggles to plan (REVIEW-03 §3).
	 */
	static final int MAX_TERMS = 6;

	/**
	 * Suggestions start at this many characters, the white space around them
	 * not counted. Below it the trigram index of search_normalized cannot
	 * help, and the clerk has not typed enough to choose from.
	 */
	static final int MIN_SUGGESTION_LENGTH = 3;

	/** Suggestions in all: four per group, and what one leaves goes to the other. */
	static final int MAX_SUGGESTIONS = 8;

	// Matched against the input after accents are removed and letters
	// upper-cased. Never ^.{17}$ for a VIN, which would take any 17
	// characters, a long surname included (NOTES).
	private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
	private static final Pattern PLATE = Pattern.compile("^\\p{L}{3}[\\s\\p{Pd}]?[0-9]{4}$");
	private static final Pattern TAX_ID = Pattern.compile("^[0-9]{9}$");
	private static final Pattern MOBILE = Pattern.compile("^6[0-9]{9}$");
	private static final Pattern PHONE = Pattern.compile("^2[0-9]{9}$");
	private static final Pattern POLICY_NUMBER = Pattern.compile("^21[0-9]{8}$");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern LIKE_WILDCARDS = Pattern.compile("[\\\\%_]");
	private static final char LIKE_ESCAPE = '\\';

	// Between the parts of a suggestion's second line.
	private static final String DETAIL_SEPARATOR = " · ";

	// The customer list's order (Task 15): Greek alphabetical, from the name_sort
	// column, whatever collation the database was created with.
	private static final Sort CUSTOMERS_BY_NAME = Sort.by("nameSort", "id");
	private static final Sort VEHICLES_BY_PLATE = Sort.by("plateNormalized", "id");
	// One more than shown, to tell whether there are more.
	private static final Limit FETCH_LIMIT = Limit.of(MAX_HITS + 1);

	private final CustomerRepository customerRepository;
	private final VehicleRepository vehicleRepository;
	private final HitAssembler hitAssembler;

	// For the suggestions, which open a transaction only once the input is
	// long enough (see suggest).
	private final TransactionTemplate readOnlyTransaction;

	public SearchService(CustomerRepository customerRepository, VehicleRepository vehicleRepository,
			HitAssembler hitAssembler, PlatformTransactionManager transactionManager) {
		this.customerRepository = customerRepository;
		this.vehicleRepository = vehicleRepository;
		this.hitAssembler = hitAssembler;
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
	}

	/**
	 * @param query the input as typed; blank finds nothing
	 */
	@Transactional(readOnly = true)
	public SearchResultDto search(String query) {
		Found found = find(input(query));
		return new SearchResultDto(query, found.types(), hitAssembler.customerHits(found.customers()),
				hitAssembler.vehicleHits(found.vehicles()), found.truncated());
	}

	/**
	 * The suggestions under the header's search box (Task 21a): the first rows
	 * that {@link #search} shows for the same input, at most
	 * {@value #MAX_SUGGESTIONS}, with the number of rows the page shows.
	 * <p>
	 * Below {@value #MIN_SUGGESTION_LENGTH} characters there are none, whatever
	 * the browser sends, and the database is not asked: the answer comes before
	 * a transaction, and so before a connection is taken from the pool. That is
	 * why the transaction is opened here, by hand, instead of by
	 * {@code @Transactional}.
	 *
	 * @param query the input as typed
	 */
	public SearchSuggestionsDto suggest(String query) {
		String input = input(query);
		if (input.codePointCount(0, input.length()) < MIN_SUGGESTION_LENGTH) {
			return SearchSuggestionsDto.none(query);
		}
		return readOnlyTransaction.execute(status -> suggestions(query, find(input)));
	}

	// Accents removed and letters upper-cased, as search_normalized is, and
	// without the white space around it.
	private static String input(String query) {
		return TextNormalizationUtils.normalizeText(firstCharacters(query)).strip();
	}

	/**
	 * The rows the search page shows, before their counts and owners are
	 * loaded: at most {@value #MAX_HITS} per group, customers by name and
	 * vehicles by plate.
	 *
	 * @param input     as {@link #input} gives it
	 * @param truncated a group had more matches than it keeps
	 */
	private record Found(String input, List<SearchType> types, List<Customer> customers, List<Vehicle> vehicles,
			boolean truncated) {
	}

	private Found find(String input) {
		List<SearchType> types = detectTypes(input);
		List<Customer> customers = new ArrayList<>();
		List<Vehicle> vehicles = new ArrayList<>();
		for (SearchType type : types) {
			switch (type) {
				case VIN -> vehicleRepository.findByVin(input).ifPresent(vehicles::add);
				case PLATE -> vehicleRepository.findByPlateNormalized(TextNormalizationUtils.normalizePlate(input))
						.ifPresent(vehicles::add);
				case TAX_ID -> customerRepository.findByTaxId(input).ifPresent(customers::add);
				case MOBILE -> customers.addAll(customerRepository.findByMobile(input, CUSTOMERS_BY_NAME, FETCH_LIMIT));
				case PHONE -> customers.addAll(customerRepository.findByPhone(input, CUSTOMERS_BY_NAME, FETCH_LIMIT));
				case POLICY_NUMBER -> vehicleRepository.findByPolicyNumber(input).ifPresent(vehicles::add);
				case TEXT -> {
					List<String> words = WHITESPACE.splitAsStream(input).limit(MAX_TERMS).toList();
					customers.addAll(customerRepository.findBy(customerText(words),
							found -> found.sortBy(CUSTOMERS_BY_NAME).limit(FETCH_LIMIT.max()).all()));
					vehicles.addAll(vehicleRepository.findBy(vehicleText(words),
							found -> found.sortBy(VEHICLES_BY_PLATE).limit(FETCH_LIMIT.max()).all()));
				}
			}
		}
		boolean truncated = customers.size() > MAX_HITS || vehicles.size() > MAX_HITS;
		return new Found(input, types, firstHits(customers), firstHits(vehicles), truncated);
	}

	private SearchSuggestionsDto suggestions(String query, Found found) {
		// Four per group; what one group leaves goes to the other, so 2
		// customers leave room for 6 vehicles. In the page's order, the first
		// rows of each group.
		int customerCount = Math.min(found.customers().size(),
				MAX_SUGGESTIONS - Math.min(found.vehicles().size(), MAX_SUGGESTIONS / 2));
		int vehicleCount = Math.min(found.vehicles().size(), MAX_SUGGESTIONS - customerCount);
		// Counts and owners for these rows only, not for all the page would show.
		List<Suggestion> customers = hitAssembler.customerHits(found.customers().subList(0, customerCount)).stream()
				.map(SearchService::suggestion)
				.toList();
		// A policy number finds its vehicle, and ten digits from 21 are also
		// searched as a landline, which finds customers only: every vehicle
		// here came from the policy.
		String policyNumber = found.types().contains(SearchType.POLICY_NUMBER) ? found.input() : null;
		List<Suggestion> vehicles = hitAssembler.vehicleHits(found.vehicles().subList(0, vehicleCount)).stream()
				.map(hit -> suggestion(hit, policyNumber))
				.toList();

		List<Group> groups = new ArrayList<>();
		if (!customers.isEmpty()) {
			groups.add(new Group("Πελάτες", customers));
		}
		if (!vehicles.isEmpty()) {
			groups.add(new Group("Οχήματα", vehicles));
		}
		int total = found.customers().size() + found.vehicles().size();
		// «N+», as the page says «Πάρα πολλά αποτελέσματα» when a group was cut.
		return new SearchSuggestionsDto(query, groups, total, found.truncated(),
				found.truncated() ? total + "+" : String.valueOf(total));
	}

	// «Αλεξίου Κωνσταντίνος» · «ΑΦΜ 189856820 · 2 οχήματα». A missing ΑΦΜ is
	// «ΑΦΜ —», as its cell on the page.
	private static Suggestion suggestion(CustomerHit hit) {
		return new Suggestion(name(hit.lastName(), hit.firstName()),
				"ΑΦΜ " + (hit.taxId() == null ? "—" : hit.taxId()) + DETAIL_SEPARATOR + vehicles(hit.vehicleCount()),
				"/customers/" + hit.id());
	}

	// «NZA8812» · «Opel Astra J · Αλεξίου Κωνσταντίνος», without the owner when
	// there is no current primary owner; or «συμβόλαιο 2100000001» for the
	// vehicle a policy number found.
	private static Suggestion suggestion(VehicleHit hit, String policyNumber) {
		String detail;
		if (policyNumber != null) {
			detail = "συμβόλαιο " + policyNumber;
		} else if (hit.primaryOwnerId() == null) {
			detail = hit.brand() + " " + hit.model();
		} else {
			detail = hit.brand() + " " + hit.model() + DETAIL_SEPARATOR
					+ name(hit.primaryOwnerLastName(), hit.primaryOwnerFirstName());
		}
		return new Suggestion(hit.plate(), detail, "/vehicles/" + hit.id());
	}

	private static String vehicles(long count) {
		return count == 1 ? "1 όχημα" : count + " οχήματα";
	}

	private static String name(String lastName, String firstName) {
		return firstName == null ? lastName : lastName + " " + firstName;
	}

	// Cut by code point, so a character outside the BMP is never split in two.
	private static String firstCharacters(String query) {
		if (query == null || query.codePointCount(0, query.length()) <= MAX_INPUT_LENGTH) {
			return query;
		}
		return query.substring(0, query.offsetByCodePoints(0, MAX_INPUT_LENGTH));
	}

	private static List<SearchType> detectTypes(String input) {
		if (input.isEmpty()) {
			return List.of();
		}
		if (VIN.matcher(input).matches()) {
			return List.of(SearchType.VIN);
		}
		if (PLATE.matcher(input).matches()) {
			return List.of(SearchType.PLATE);
		}
		if (TAX_ID.matcher(input).matches()) {
			return List.of(SearchType.TAX_ID);
		}
		if (MOBILE.matcher(input).matches()) {
			return List.of(SearchType.MOBILE);
		}
		// A landline and a policy number look alike, and the clerk should not
		// have to know which one they typed (CLAUDE.md, resolved conflict 3).
		if (POLICY_NUMBER.matcher(input).matches()) {
			return List.of(SearchType.PHONE, SearchType.POLICY_NUMBER);
		}
		if (PHONE.matcher(input).matches()) {
			return List.of(SearchType.PHONE);
		}
		return List.of(SearchType.TEXT);
	}

	// Every word must appear, in any order: "Μαρία Αλεξίου" and "αλεξ μαρ"
	// both find Αλεξίου Μαρία.
	private static PredicateSpecification<Customer> customerText(List<String> words) {
		return (from, cb) -> {
			Expression<String> searchNormalized = from.get("searchNormalized");
			return cb.and(words.stream()
					.map(word -> contains(cb, searchNormalized, word))
					.toArray(Predicate[]::new));
		};
	}

	// search_normalized holds the plate as plate_normalized does, look-alike
	// letters Latin and no dash, so each word is also tried in that form:
	// "ΑΒΕ-12" finds ΑΒΕ1234.
	private static PredicateSpecification<Vehicle> vehicleText(List<String> words) {
		return (from, cb) -> {
			Expression<String> searchNormalized = from.get("searchNormalized");
			return cb.and(words.stream()
					.map(word -> {
						Predicate asText = contains(cb, searchNormalized, word);
						String plate = TextNormalizationUtils.normalizePlate(word);
						return plate.isEmpty() || plate.equals(word)
								? asText
								: cb.or(asText, contains(cb, searchNormalized, plate));
					})
					.toArray(Predicate[]::new));
		};
	}

	// A plain LIKE on the column itself, so the trigram index can serve it.
	// %, _ and \ typed by the clerk are matched literally.
	private static Predicate contains(CriteriaBuilder cb, Expression<String> column, String word) {
		String literal = LIKE_WILDCARDS.matcher(word).replaceAll("\\\\$0");
		return cb.like(column, "%" + literal + "%", LIKE_ESCAPE);
	}

	private static <T> List<T> firstHits(List<T> hits) {
		return hits.size() > MAX_HITS ? hits.subList(0, MAX_HITS) : hits;
	}

}
