package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.SavedCustomerDto;
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
import gr.insuranceoffice.service.BusinessException.Violation;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerServiceTest {

	private static final LocalDate TODAY = LocalDate.now();

	private static final String MISSING_MOBILE =
			"Το κινητό είναι υποχρεωτικό για τον κύριο ιδιοκτήτη οχήματος με τρέχον συμβόλαιο.";

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

	@BeforeEach
	void startEmpty() {
		truncateTables();
	}

	// Other test classes share this database.
	@AfterEach
	void leaveEmpty() {
		truncateTables();
	}

	@Test
	void savesACustomerWithoutMobile() {
		SavedCustomerDto saved = customerService.create(form().mobile(null).dto());

		assertThat(saved.warnings()).isEmpty();
		assertThat(saved.customer().id()).isNotNull();
		assertThat(saved.customer().version()).isZero();
		assertThat(customerRepository.findById(saved.customer().id())).get().extracting(Customer::getMobile)
				.isNull();
	}

	// DECISIONS §2.
	@Test
	void warnsButSavesWithoutTaxId() {
		SavedCustomerDto saved = customerService.create(form().taxId(null).dto());

		assertThat(saved.warnings()).containsExactly("Λείπει το ΑΦΜ του πελάτη.");
		assertThat(customerRepository.count()).isEqualTo(1);
	}

	// A form sends empty fields as "". Stored as such, the second customer
	// without ΑΦΜ would break the unique index.
	@Test
	void storesBlankFieldsAsMissing() {
		SavedCustomerDto first = customerService.create(form().taxId("").mobile(" ").email("").dto());
		SavedCustomerDto second = customerService.create(form().taxId("  ").lastName("Βασιλείου").dto());

		assertThat(first.warnings()).containsExactly("Λείπει το ΑΦΜ του πελάτη.");
		assertThat(second.warnings()).containsExactly("Λείπει το ΑΦΜ του πελάτη.");
		assertThat(customerRepository.findById(first.customer().id())).get()
				.extracting(Customer::getTaxId, Customer::getMobile, Customer::getEmail)
				.containsOnlyNulls();
	}

	@Test
	void reportsEveryInvalidFieldAtOnce() {
		CustomerDto dto = form().lastName(" ").entityType("PERSON").taxId("900000081").mobile("2101234567")
				.phone("6900000001").postalCode("1234").email("χωρίς παπάκι").dto();

		assertThatThrownBy(() -> customerService.create(dto))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.extracting(Violation::field)
						.containsExactly("lastName", "entityType", "taxId", "mobile", "phone", "postalCode", "email"));
		assertThat(customerRepository.count()).isZero();
	}

	@Test
	void refusesATaxIdThatAnotherCustomerHas() {
		customerService.create(form().dto());

		assertThatThrownBy(() -> customerService.create(form().lastName("Βασιλείου").dto()))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.containsExactly(new Violation("taxId", "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ.")));
	}

	@Test
	void updatesAndReturnsTheNewVersion() {
		CustomerDto created = customerService.create(form().dto()).customer();

		SavedCustomerDto updated = customerService.update(created.id(), Form.of(created).city("Λάρισα").dto());

		assertThat(updated.customer().version()).isEqualTo(1);
		assertThat(updated.customer().city()).isEqualTo("Λάρισα");
		// Keeping its own ΑΦΜ is not a duplicate.
		assertThat(updated.customer().taxId()).isEqualTo("900000080");
	}

	// SPEC §9: the second of two users editing the same customer is refused.
	@Test
	void refusesAnUpdateBasedOnAnOutdatedVersion() {
		CustomerDto opened = customerService.create(form().dto()).customer();
		customerService.update(opened.id(), Form.of(opened).mobile("6900000001").dto());

		assertThatThrownBy(() -> customerService.update(opened.id(), Form.of(opened).mobile("6900000002").dto()))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(customerRepository.findById(opened.id())).get().extracting(Customer::getMobile)
				.isEqualTo("6900000001");
	}

	@Test
	void refusesToUpdateACustomerThatNoLongerExists() {
		assertThatThrownBy(() -> customerService.update(999L, form().version(0L).dto()))
				.isInstanceOf(NotFoundException.class);
	}

	/** DECISIONS §1: mobile is required only for the primary owner of an insured vehicle. */
	@Nested
	class MobileOfThePrimaryOwner {

		@Test
		void isRequiredWhileTheVehicleHasACurrentPolicy() {
			CustomerDto owner = customerService.create(form().mobile("6900000001").dto()).customer();
			owns(owner, true, null, TODAY.minusMonths(3), TODAY.plusMonths(3));

			assertThatThrownBy(() -> customerService.update(owner.id(), Form.of(owner).mobile(null).dto()))
					.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
							.containsExactly(new Violation("mobile", MISSING_MOBILE)));
			assertThat(customerRepository.findById(owner.id())).get().extracting(Customer::getMobile)
					.isEqualTo("6900000001");
		}

		// «Τρέχον» = CURRENT_DATE BETWEEN start_date AND end_date.
		@Test
		void isRequiredOnTheFirstAndLastDayOfThePolicy() {
			CustomerDto startsToday = customerService.create(form().dto()).customer();
			owns(startsToday, true, null, TODAY, TODAY.plusMonths(6));
			CustomerDto endsToday = customerService.create(form().taxId("900000091").dto()).customer();
			owns(endsToday, true, null, TODAY.minusMonths(6), TODAY);

			assertThatThrownBy(() -> customerService.update(startsToday.id(), Form.of(startsToday).dto()))
					.isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> customerService.update(endsToday.id(), Form.of(endsToday).dto()))
					.isInstanceOf(BusinessException.class);
		}

		@Test
		void isNotRequiredForACoOwner() {
			CustomerDto coOwner = customerService.create(form().dto()).customer();
			owns(coOwner, false, null, TODAY.minusMonths(3), TODAY.plusMonths(3));

			assertThat(customerService.update(coOwner.id(), Form.of(coOwner).city("Λάρισα").dto()).customer().city())
					.isEqualTo("Λάρισα");
		}

		@Test
		void isNotRequiredForAFormerOwner() {
			CustomerDto former = customerService.create(form().dto()).customer();
			owns(former, true, TODAY.minusMonths(1), TODAY.minusMonths(3), TODAY.plusMonths(3));

			assertThat(customerService.update(former.id(), Form.of(former).dto()).customer().version()).isZero();
		}

		@Test
		void isNotRequiredWhenThePolicyHasExpiredOrNotStarted() {
			CustomerDto expired = customerService.create(form().dto()).customer();
			owns(expired, true, null, TODAY.minusYears(1), TODAY.minusDays(1));
			CustomerDto future = customerService.create(form().taxId("900000091").dto()).customer();
			owns(future, true, null, TODAY.plusDays(1), TODAY.plusMonths(6));

			assertThat(customerService.update(expired.id(), Form.of(expired).dto()).warnings()).isEmpty();
			assertThat(customerService.update(future.id(), Form.of(future).dto()).warnings()).isEmpty();
		}

	}

	@Nested
	class TaxIdCheckDigit {

		@Test
		void acceptsNineDigitsWithTheRightCheckDigit() {
			assertThat(CustomerService.isValidTaxId("123456783")).isTrue();
			assertThat(CustomerService.isValidTaxId("900000080")).isTrue();
			assertThat(CustomerService.isValidTaxId("900000091")).isTrue();
		}

		@Test
		void refusesAWrongCheckDigitOrShape() {
			assertThat(CustomerService.isValidTaxId("123456789")).isFalse();
			assertThat(CustomerService.isValidTaxId("12345678")).isFalse();
			assertThat(CustomerService.isValidTaxId("1234567830")).isFalse();
			assertThat(CustomerService.isValidTaxId("12345678Α")).isFalse();
			assertThat(CustomerService.isValidTaxId(null)).isFalse();
		}

	}

	private int vehicles;

	// One vehicle per call, owned 100% by the customer, with one policy.
	private void owns(CustomerDto owner, boolean primary, LocalDate toDate, LocalDate policyStart,
			LocalDate policyEnd) {
		vehicles++;
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW12345" + vehicles);
		vehicle.setPlate("ΑΒΕ-123" + vehicles);
		vehicle.setBrand("Volkswagen");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("M1");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("Λευκό");
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		vehicle = vehicleRepository.save(vehicle);

		Ownership ownership = new Ownership();
		ownership.setVehicle(vehicle);
		ownership.setCustomer(customerRepository.getReferenceById(owner.id()));
		ownership.setPercentage(new BigDecimal("100"));
		ownership.setPrimary(primary);
		ownership.setToDate(toDate);
		ownershipRepository.save(ownership);

		Policy policy = new Policy();
		policy.setVehicle(vehicle);
		policy.setPolicyNumber("210000000" + vehicles);
		policy.setInsuranceCompany("Northwind");
		policy.setStartDate(policyStart);
		policy.setEndDate(policyEnd);
		policy.setPremium(new BigDecimal("120.00"));
		policyRepository.save(policy);
	}

	private static Form form() {
		return new Form();
	}

	/** A customer form as the clerk would fill it in; valid unless changed. */
	private static final class Form {

		private Long id;
		private String taxId = "900000080";
		private String entityType = "INDIVIDUAL";
		private String lastName = "Αλεξίου";
		private String firstName = "Μαρία";
		private String city;
		private String postalCode;
		private String mobile;
		private String phone;
		private String email;
		private Long version;

		static Form of(CustomerDto dto) {
			Form form = new Form();
			form.id = dto.id();
			form.taxId = dto.taxId();
			form.entityType = dto.entityType();
			form.lastName = dto.lastName();
			form.firstName = dto.firstName();
			form.city = dto.city();
			form.postalCode = dto.postalCode();
			form.mobile = dto.mobile();
			form.phone = dto.phone();
			form.email = dto.email();
			form.version = dto.version();
			return form;
		}

		Form taxId(String value) {
			taxId = value;
			return this;
		}

		Form entityType(String value) {
			entityType = value;
			return this;
		}

		Form lastName(String value) {
			lastName = value;
			return this;
		}

		Form city(String value) {
			city = value;
			return this;
		}

		Form postalCode(String value) {
			postalCode = value;
			return this;
		}

		Form mobile(String value) {
			mobile = value;
			return this;
		}

		Form phone(String value) {
			phone = value;
			return this;
		}

		Form email(String value) {
			email = value;
			return this;
		}

		Form version(Long value) {
			version = value;
			return this;
		}

		CustomerDto dto() {
			return new CustomerDto(id, taxId, entityType, lastName, firstName, null, null, null, null, null, city,
					postalCode, mobile, phone, email, null, version);
		}

	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
