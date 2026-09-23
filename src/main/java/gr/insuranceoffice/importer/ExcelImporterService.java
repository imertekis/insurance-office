package gr.insuranceoffice.importer;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.entity.Customer;
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.entity.Ownership;
import gr.insuranceoffice.entity.Policy;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.importer.ExcelValues.Surcharge;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.OwnershipRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.OwnershipService;
import gr.insuranceoffice.service.OwnershipService.Share;
import gr.insuranceoffice.service.VehicleService;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * Imports the office's two Excel files (SPEC §10): the customer details, and
 * the archive with one row per vehicle, its owners and its current policy.
 * <p>
 * All or nothing, in one transaction. Every row is checked and every problem
 * collected; if there is any, {@link ExcelImportException} reports them all
 * and the transaction rolls back. Running the import again updates rather
 * than duplicates: rows are matched by natural key (ΑΦΜ, VIN, policy number,
 * intermediary name) and overwritten from the file.
 * <p>
 * No normalization or business rule is repeated here. Plates and VINs are
 * normalized by {@link Vehicle}'s setters and callbacks through
 * {@link TextNormalizationUtils}, search columns by the database, the VIN
 * format is {@link VehicleService#isVin}, the ownership rule is checked by
 * {@link OwnershipService}, and a rule the database enforces, such as a policy
 * ending after it starts, is reported as a refused row.
 */
@Service
public class ExcelImporterService {

	static final String CUSTOMER_FILE = "Στοιχεία πελατών";
	static final String ARCHIVE_FILE = "Αρχείο οχημάτων και συμβολαίων";

	// Customer file columns.
	private static final String LAST_NAME = "Επώνυμο";
	private static final String FIRST_NAME = "Όνομα";
	private static final String FATHER_NAME = "Πατρώνυμο";
	private static final String STREET = "Οδός";
	private static final String CITY = "Πόλη";
	private static final String POSTAL_CODE = "Τ.Κ.";
	private static final String TAX_ID = "Α.Φ.Μ.";
	private static final String BIRTH_DATE = "Ημερομηνία Γέννησης";
	private static final String TAX_OFFICE = "Δ.Ο.Υ.";
	private static final String MOBILE = "Κινητό Τηλέφωνο";
	private static final String PHONE = "Σταθερό Τηλέφωνο";
	private static final String EMAIL = "Email";
	private static final String LICENSE_DATE = "Ημ. Απόκτησης Διπλώματος";

	private static final List<String> CUSTOMER_COLUMNS = List.of(LAST_NAME, FIRST_NAME, FATHER_NAME, STREET, CITY,
			POSTAL_CODE, TAX_ID, BIRTH_DATE, TAX_OFFICE, MOBILE, PHONE, EMAIL, LICENSE_DATE);

	// Archive file columns. The codes in brackets are the fields of the vehicle
	// licence, written in Latin letters as in the file. The owners' name
	// columns are not read: owners are linked to the customer file by ΑΦΜ,
	// never by name.
	private static final String POLICY_NUMBER = "Αρ. Συμβολαίου";
	private static final String PLATE = "Αρ. Κυκλοφορίας (A)";
	private static final String LICENSE_STREET = "Οδός (C.1.3)";
	private static final String LICENSE_CITY = "Πόλη";
	private static final String LICENSE_POSTAL_CODE = "Τ.Κ.";
	private static final String OWNER_TAX_ID = "Α.Φ.Μ.";
	private static final String OWNER_SHARE = "Ποσοστό Ιδιοκτησίας (Κύριος %)";
	private static final String CO_OWNER_TAX_ID = "Α.Φ.Μ. Συνιδιοκτήτη";
	private static final String CO_OWNER_SHARE = "Ποσοστό Ιδιοκτησίας (Συνιδιοκτήτης %)";
	private static final String BRAND = "Μάρκα (D.1)";
	private static final String MODEL = "Μοντέλο (D.3)";
	private static final String FIRST_REGISTRATION = "1η Άδεια (B)";
	private static final String LICENSE_ISSUE_DATE = "Έκδοση (I)";
	private static final String VIN = "Αρ. Πλαισίου / VIN (E)";
	private static final String CATEGORY = "Κατηγορία (J)";
	private static final String USAGE = "Χρήση Οχήματος";
	private static final String COLOR = "Χρώμα (R)";
	private static final String SEATS = "Θέσεις (S.1)";
	private static final String ENGINE_CC = "Κυβικά (P.1)";
	private static final String POWER_KW = "Ισχύς kW (P.2)";
	private static final String FUEL = "Καύσιμο (P.3)";
	private static final String ENGINE_NUMBER = "Αρ. Κινητήρα (P.5)";
	private static final String CO2 = "CO2 (V.7)";
	private static final String EMISSION_STANDARD = "Euro (V.9)";
	private static final String WEIGHT = "Βάρος kg (G)";
	private static final String START_DATE = "Έναρξη Ασφάλειας";
	private static final String END_DATE = "Λήξη Ασφάλειας";
	private static final String INSURANCE_COMPANY = "Ασφαλιστική Εταιρεία";
	private static final String SURCHARGE = "Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)";
	private static final String PREMIUM = "Πληρωτέα Μικτά Ασφάλιστρα";
	private static final String INTERMEDIARY = "Διαμεσολαβούν Πρόσωπο";

	private static final List<String> ARCHIVE_COLUMNS = List.of(POLICY_NUMBER, PLATE, LICENSE_STREET, LICENSE_CITY,
			LICENSE_POSTAL_CODE, OWNER_TAX_ID, OWNER_SHARE, CO_OWNER_TAX_ID, CO_OWNER_SHARE, BRAND, MODEL,
			FIRST_REGISTRATION, LICENSE_ISSUE_DATE, VIN, CATEGORY, USAGE, COLOR, SEATS, ENGINE_CC, POWER_KW, FUEL,
			ENGINE_NUMBER, CO2, EMISSION_STANDARD, WEIGHT, START_DATE, END_DATE, INSURANCE_COMPANY, SURCHARGE,
			PREMIUM, INTERMEDIARY);

	private final CustomerRepository customerRepository;
	private final VehicleRepository vehicleRepository;
	private final OwnershipRepository ownershipRepository;
	private final PolicyRepository policyRepository;
	private final IntermediaryRepository intermediaryRepository;

	public ExcelImporterService(CustomerRepository customerRepository, VehicleRepository vehicleRepository,
			OwnershipRepository ownershipRepository, PolicyRepository policyRepository,
			IntermediaryRepository intermediaryRepository) {
		this.customerRepository = customerRepository;
		this.vehicleRepository = vehicleRepository;
		this.ownershipRepository = ownershipRepository;
		this.policyRepository = policyRepository;
		this.intermediaryRepository = intermediaryRepository;
	}

	/**
	 * @throws ExcelImportException with every problem found; nothing is kept
	 */
	@Transactional
	public ImportResult importFiles(InputStream customerFile, InputStream archiveFile) {
		List<ImportError> errors = new ArrayList<>();
		List<ExcelRow> customerRows = ExcelSheet.read(customerFile, CUSTOMER_FILE, CUSTOMER_COLUMNS, errors);
		List<ExcelRow> archiveRows = ExcelSheet.read(archiveFile, ARCHIVE_FILE, ARCHIVE_COLUMNS, errors);
		if (!errors.isEmpty()) {
			throw new ExcelImportException(errors);
		}

		Run run = new Run();
		// Customers first, so that archive rows can link to them.
		importRows(customerRows, row -> importCustomer(row, run), errors);
		importRows(archiveRows, row -> importArchiveRow(row, run), errors);
		if (!errors.isEmpty()) {
			throw new ExcelImportException(errors);
		}
		return run.result();
	}

	private void importRows(List<ExcelRow> rows, Consumer<ExcelRow> importRow, List<ImportError> errors) {
		for (ExcelRow row : rows) {
			try {
				importRow.accept(row);
				// Flushes the whole unit of work, so that a row the database
				// refuses is reported as that row.
				customerRepository.flush();
			} catch (DataIntegrityViolationException e) {
				String reason = String.valueOf(e.getMostSpecificCause().getMessage()).lines().findFirst().orElse("");
				row.reject(null, "η βάση δεδομένων απέρριψε τη γραμμή: " + reason);
				// PostgreSQL refuses every further statement in this transaction.
				throw new ExcelImportException(errors);
			}
		}
	}

	private void importCustomer(ExcelRow row, Run run) {
		String taxId = row.requiredText(TAX_ID);
		Consumer<Customer> fields = readCustomerFields(row, taxId);
		if (taxId != null && !run.customerTaxIds.add(taxId)) {
			row.reject(TAX_ID, "ο ίδιος ΑΦΜ υπάρχει σε προηγούμενη γραμμή");
		}
		if (row.isRejected()) {
			return;
		}

		Customer customer = findOrCreate(customerRepository.findByTaxId(taxId), Customer::new, run.customers);
		fields.accept(customer);
		run.customersByTaxId.put(taxId, customerRepository.save(customer));
	}

	// Reads the whole row before anything is written, so that a managed
	// entity is never left half-updated with an invalid value.
	private static Consumer<Customer> readCustomerFields(ExcelRow row, String taxId) {
		String lastName = row.requiredText(LAST_NAME);
		String firstName = row.text(FIRST_NAME);
		String fatherName = row.text(FATHER_NAME);
		String street = row.text(STREET);
		String city = row.text(CITY);
		String postalCode = row.text(POSTAL_CODE);
		LocalDate birthDate = row.date(BIRTH_DATE);
		String taxOffice = row.text(TAX_OFFICE);
		String mobile = row.text(MOBILE);
		String phone = row.text(PHONE);
		String email = row.text(EMAIL);
		LocalDate licenseDate = row.date(LICENSE_DATE);

		return customer -> {
			customer.setTaxId(taxId);
			customer.setLastName(lastName);
			customer.setFirstName(firstName);
			customer.setFatherName(fatherName);
			customer.setStreet(street);
			customer.setCity(city);
			customer.setPostalCode(postalCode);
			customer.setBirthDate(birthDate);
			customer.setTaxOffice(taxOffice);
			customer.setMobile(mobile);
			customer.setPhone(phone);
			customer.setEmail(email);
			customer.setLicenseDate(licenseDate);
		};
	}

	private void importArchiveRow(ExcelRow row, Run run) {
		// As Vehicle stores it, so that "wvw…" and "WVW…" are one VIN, here and
		// against the database (Task 17).
		String vin = TextNormalizationUtils.storedVin(row.requiredText(VIN));
		String plate = row.requiredText(PLATE);
		String policyNumber = row.requiredText(POLICY_NUMBER);
		Consumer<Vehicle> vehicleFields = readVehicleFields(row, vin, plate);
		Consumer<Policy> policyFields = readPolicyFields(row, policyNumber);
		List<Owner> owners = readOwners(row, run);
		String intermediaryName = row.text(INTERMEDIARY);
		if (vin != null && !VehicleService.isVin(vin)) {
			row.reject(VIN, "το VIN έχει 17 χαρακτήρες, χωρίς τα γράμματα I, O και Q");
		}
		if (vin != null && !run.vins.add(vin)) {
			row.reject(VIN, "το ίδιο VIN υπάρχει σε προηγούμενη γραμμή");
		}
		// Compared as the unique index compares them: ΑΒΕ1234, abe1234 and
		// ABE1234 are one plate. A re-import finds its own vehicle, by VIN.
		if (plate != null) {
			String plateKey = TextNormalizationUtils.normalizePlate(plate);
			if (!run.plates.add(plateKey)) {
				row.reject(PLATE, "η ίδια πινακίδα υπάρχει σε προηγούμενη γραμμή");
			} else {
				vehicleRepository.findByPlateNormalized(plateKey)
						.filter(other -> !other.getVin().equals(vin))
						.ifPresent(other -> row.reject(PLATE,
								"η πινακίδα ανήκει ήδη σε άλλο όχημα (VIN " + other.getVin() + ")"));
			}
		}
		if (policyNumber != null && !run.policyNumbers.add(policyNumber)) {
			row.reject(POLICY_NUMBER, "ο ίδιος αριθμός συμβολαίου υπάρχει σε προηγούμενη γραμμή");
		}
		if (row.isRejected()) {
			return;
		}

		Vehicle vehicle = findOrCreate(vehicleRepository.findByVin(vin), Vehicle::new, run.vehicles);
		vehicleFields.accept(vehicle);
		vehicleRepository.save(vehicle);
		syncOwnerships(vehicle, owners, run.ownerships);

		Policy policy = findOrCreate(policyRepository.findByPolicyNumber(policyNumber), Policy::new, run.policies);
		policyFields.accept(policy);
		policy.setVehicle(vehicle);
		policy.setIntermediary(intermediaryName == null ? null : findOrCreateIntermediary(intermediaryName, run));
		policyRepository.save(policy);
	}

	private static Consumer<Vehicle> readVehicleFields(ExcelRow row, String vin, String plate) {
		String brand = row.requiredText(BRAND);
		String model = row.requiredText(MODEL);
		LocalDate firstRegistration = row.requiredDate(FIRST_REGISTRATION);
		LocalDate licenseIssueDate = row.date(LICENSE_ISSUE_DATE);
		String category = row.requiredText(CATEGORY);
		UsageType usageType = row.requiredValue(USAGE, text -> ExcelValues.parseEnum(text, UsageType.class));
		String color = row.requiredText(COLOR);
		Short seats = row.value(SEATS, ExcelValues::parseShort);
		Integer engineCc = row.value(ENGINE_CC, ExcelValues::parseInteger);
		BigDecimal powerKw = row.requiredValue(POWER_KW, ExcelValues::parseDecimal);
		FuelType fuelType = row.requiredValue(FUEL, text -> ExcelValues.parseEnum(text, FuelType.class));
		String engineNumber = row.text(ENGINE_NUMBER);
		Integer co2 = row.value(CO2, ExcelValues::parseInteger);
		String emissionStandard = row.text(EMISSION_STANDARD);
		Integer weightKg = row.value(WEIGHT, ExcelValues::parseInteger);
		String licenseStreet = row.text(LICENSE_STREET);
		String licenseCity = row.text(LICENSE_CITY);
		String licensePostalCode = row.text(LICENSE_POSTAL_CODE);
		// SPEC §10: an electric vehicle's 0 cc means "no engine" and is stored as NULL.
		Integer storedEngineCc = fuelType == FuelType.ΗΛΕΚΤΡΙΣΜΟΣ && Integer.valueOf(0).equals(engineCc)
				? null
				: engineCc;

		return vehicle -> {
			vehicle.setVin(vin);
			vehicle.setPlate(plate);
			vehicle.setBrand(brand);
			vehicle.setModel(model);
			vehicle.setFirstRegistration(firstRegistration);
			vehicle.setLicenseIssueDate(licenseIssueDate);
			vehicle.setCategory(category);
			vehicle.setUsageType(usageType);
			vehicle.setColor(color);
			vehicle.setSeats(seats);
			vehicle.setEngineCc(storedEngineCc);
			vehicle.setPowerKw(powerKw);
			vehicle.setFuelType(fuelType);
			vehicle.setEngineNumber(engineNumber);
			vehicle.setCo2(co2);
			vehicle.setEmissionStandard(emissionStandard);
			vehicle.setWeightKg(weightKg);
			vehicle.setLicenseStreet(licenseStreet);
			vehicle.setLicenseCity(licenseCity);
			vehicle.setLicensePostalCode(licensePostalCode);
		};
	}

	private static Consumer<Policy> readPolicyFields(ExcelRow row, String policyNumber) {
		String insuranceCompany = row.requiredText(INSURANCE_COMPANY);
		LocalDate startDate = row.requiredDate(START_DATE);
		LocalDate endDate = row.requiredDate(END_DATE);
		BigDecimal premium = row.requiredValue(PREMIUM, ExcelValues::parseMoney);
		Surcharge surcharge = row.requiredValue(SURCHARGE, ExcelValues::parseSurcharge);

		return policy -> {
			policy.setPolicyNumber(policyNumber);
			policy.setInsuranceCompany(insuranceCompany);
			policy.setStartDate(startDate);
			policy.setEndDate(endDate);
			policy.setPremium(premium);
			policy.setSurcharge(surcharge.applies());
			policy.setSurchargeType(surcharge.type());
		};
	}

	private List<Owner> readOwners(ExcelRow row, Run run) {
		String ownerTaxId = row.requiredText(OWNER_TAX_ID);
		BigDecimal ownerShare = row.requiredValue(OWNER_SHARE, ExcelValues::parsePercentage);
		String coOwnerTaxId = row.text(CO_OWNER_TAX_ID);
		BigDecimal coOwnerShare = row.value(CO_OWNER_SHARE, ExcelValues::parsePercentage);
		boolean hasCoOwner = row.has(CO_OWNER_TAX_ID);
		if (hasCoOwner != row.has(CO_OWNER_SHARE)) {
			row.reject(CO_OWNER_SHARE, "ο συνιδιοκτήτης χρειάζεται και ΑΦΜ και ποσοστό");
		} else if (ownerShare != null && (!hasCoOwner || coOwnerShare != null)) {
			// Checked on the shares as written, even when an owner cannot be
			// linked, so that every problem in the row is reported at once.
			List<Share> shares = new ArrayList<>(List.of(new Share(ownerShare, true)));
			if (hasCoOwner) {
				shares.add(new Share(coOwnerShare, false));
			}
			OwnershipService.checkCurrentOwners(shares).forEach(problem -> row.reject(OWNER_SHARE, problem));
		}
		if (ownerTaxId != null && ownerTaxId.equals(coOwnerTaxId)) {
			row.reject(CO_OWNER_TAX_ID, "ίδιος ΑΦΜ με τον κύριο ιδιοκτήτη");
		}

		List<Owner> owners = new ArrayList<>();
		Customer owner = linkCustomer(row, OWNER_TAX_ID, ownerTaxId, run);
		if (owner != null) {
			owners.add(new Owner(owner, ownerShare, true));
		}
		Customer coOwner = linkCustomer(row, CO_OWNER_TAX_ID, coOwnerTaxId, run);
		if (coOwner != null) {
			owners.add(new Owner(coOwner, coOwnerShare, false));
		}
		return owners;
	}

	// By ΑΦΜ, never by name (SPEC §10).
	private Customer linkCustomer(ExcelRow row, String column, String taxId, Run run) {
		if (taxId == null) {
			return null;
		}
		Customer customer = run.customersByTaxId.get(taxId);
		if (customer == null) {
			customer = customerRepository.findByTaxId(taxId).orElse(null);
		}
		// A customer whose own row was rejected has already been reported.
		if (customer == null && !run.customerTaxIds.contains(taxId)) {
			row.reject(column, "δεν βρέθηκε πελάτης με ΑΦΜ " + taxId + " στο αρχείο πελατών ή στη βάση");
		}
		return customer;
	}

	/**
	 * Makes the vehicle's current owners match the row. The file holds only
	 * current owners, so an owner missing from it is removed; ownerships with
	 * transfer dates did not come from the import and are left alone.
	 */
	private void syncOwnerships(Vehicle vehicle, List<Owner> owners, Counter counter) {
		Map<Long, Ownership> current = new HashMap<>();
		for (Ownership ownership : ownershipRepository.findByVehicle(vehicle)) {
			if (ownership.getFromDate() == null && ownership.getToDate() == null) {
				current.put(ownership.getCustomer().getId(), ownership);
			}
		}
		for (Owner owner : owners) {
			Ownership ownership = findOrCreate(Optional.ofNullable(current.remove(owner.customer().getId())),
					Ownership::new, counter);
			ownership.setVehicle(vehicle);
			ownership.setCustomer(owner.customer());
			ownership.setPercentage(owner.share());
			ownership.setPrimary(owner.primary());
			ownershipRepository.save(ownership);
		}
		for (Ownership former : current.values()) {
			ownershipRepository.delete(former);
			counter.removed++;
		}
	}

	private Intermediary findOrCreateIntermediary(String fullName, Run run) {
		Intermediary known = run.intermediariesByName.get(fullName);
		if (known != null) {
			return known;
		}
		Intermediary intermediary = findOrCreate(intermediaryRepository.findByFullName(fullName), Intermediary::new,
				run.intermediaries);
		intermediary.setFullName(fullName);
		intermediaryRepository.save(intermediary);
		run.intermediariesByName.put(fullName, intermediary);
		return intermediary;
	}

	private static <T> T findOrCreate(Optional<T> existing, Supplier<T> create, Counter counter) {
		if (existing.isPresent()) {
			counter.updated++;
			return existing.get();
		}
		counter.created++;
		return create.get();
	}

	private record Owner(Customer customer, BigDecimal share, boolean primary) {
	}

	/** Counters and lookups for one import. */
	private static final class Run {

		final Counter intermediaries = new Counter();
		final Counter customers = new Counter();
		final Counter vehicles = new Counter();
		final Counter ownerships = new Counter();
		final Counter policies = new Counter();

		// Natural keys seen in the files, to catch a key repeated on a later row.
		final Set<String> customerTaxIds = new HashSet<>();
		final Set<String> vins = new HashSet<>();
		// As plate_normalized.
		final Set<String> plates = new HashSet<>();
		final Set<String> policyNumbers = new HashSet<>();

		final Map<String, Customer> customersByTaxId = new HashMap<>();
		final Map<String, Intermediary> intermediariesByName = new HashMap<>();

		ImportResult result() {
			return new ImportResult(intermediaries.counts(), customers.counts(), vehicles.counts(),
					ownerships.counts(), policies.counts());
		}

	}

	private static final class Counter {

		int created;
		int updated;
		int removed;

		ImportResult.Counts counts() {
			return new ImportResult.Counts(created, updated, removed);
		}

	}

}
