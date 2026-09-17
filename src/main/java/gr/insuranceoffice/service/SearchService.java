package gr.insuranceoffice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import gr.insuranceoffice.dto.SearchResultDto;
import gr.insuranceoffice.dto.SearchResultDto.CustomerHit;
import gr.insuranceoffice.dto.SearchResultDto.SearchType;
import gr.insuranceoffice.dto.SearchResultDto.VehicleHit;
import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.mapper.SearchResultMapper;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.OwnershipRepository.VehicleCount;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * The single search box (SPEC §6). The input's shape decides what it is: a
 * VIN, plate, ΑΦΜ or policy number is looked up exactly, and anything else
 * is free text matched against {@code search_normalized} through its trigram
 * index. There is no type dropdown (DECISIONS §3).
 * <p>
 * A fixed number of queries runs whatever the number of hits: vehicle counts
 * and primary owners are loaded for all hits at once.
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

	private static final Sort CUSTOMERS_BY_NAME = Sort.by("lastName", "firstName", "id");
	private static final Sort VEHICLES_BY_PLATE = Sort.by("plateNormalized", "id");
	// One more than shown, to tell whether there are more.
	private static final Limit FETCH_LIMIT = Limit.of(MAX_HITS + 1);

	private final CustomerRepository customerRepository;
	private final VehicleRepository vehicleRepository;
	private final OwnershipRepository ownershipRepository;
	private final SearchResultMapper searchResultMapper;

	public SearchService(CustomerRepository customerRepository, VehicleRepository vehicleRepository,
			OwnershipRepository ownershipRepository, SearchResultMapper searchResultMapper) {
		this.customerRepository = customerRepository;
		this.vehicleRepository = vehicleRepository;
		this.ownershipRepository = ownershipRepository;
		this.searchResultMapper = searchResultMapper;
	}

	/**
	 * @param query the input as typed; blank finds nothing
	 */
	@Transactional(readOnly = true)
	public SearchResultDto search(String query) {
		String input = TextNormalizationUtils.normalizeText(firstCharacters(query)).strip();
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
		return new SearchResultDto(query, types, customerHits(firstHits(customers)), vehicleHits(firstHits(vehicles)),
				truncated);
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

	// A plate is stored with Latin letters and no dash, so each word is also
	// tried in plate form: "ΑΒΕ-12" finds ABE-1234.
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

	private List<CustomerHit> customerHits(List<Customer> customers) {
		if (customers.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> vehicleCounts = ownershipRepository
				.countCurrentVehicles(customers.stream().map(Customer::getId).toList()).stream()
				.collect(Collectors.toMap(VehicleCount::getCustomerId, VehicleCount::getVehicleCount));
		return customers.stream()
				.map(customer -> searchResultMapper.toCustomerHit(customer,
						vehicleCounts.getOrDefault(customer.getId(), 0L)))
				.toList();
	}

	private List<VehicleHit> vehicleHits(List<Vehicle> vehicles) {
		if (vehicles.isEmpty()) {
			return List.of();
		}
		Map<Long, Customer> primaryOwners = ownershipRepository
				.findCurrentPrimaryOwners(vehicles.stream().map(Vehicle::getId).toList()).stream()
				// The ownership rule allows one primary owner; a search never
				// fails over data that breaks it.
				.collect(Collectors.toMap(ownership -> ownership.getVehicle().getId(), Ownership::getCustomer,
						(first, second) -> first));
		return vehicles.stream()
				.map(vehicle -> searchResultMapper.toVehicleHit(vehicle, primaryOwners.get(vehicle.getId())))
				.toList();
	}

}
