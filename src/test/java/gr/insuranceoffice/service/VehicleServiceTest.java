package gr.insuranceoffice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import gr.insuranceoffice.TestcontainersConfiguration;
import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Vehicle;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.service.BusinessException.Violation;
import gr.insuranceoffice.service.VehicleValues.Match;

/**
 * Task 23a: brand, category, colour and Euro take only values of their
 * lists, and the seats 1 to 99; a value from before the lists is kept while
 * it is left as it is.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VehicleServiceTest {

	@Autowired
	private VehicleService vehicleService;

	@Autowired
	private VehicleRepository vehicleRepository;

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

	// V7's brands, read as the form and the import read them: no spelling
	// names two brands, or reading them would fail.
	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "VW, Volkswagen", "V.W., Volkswagen", "VOLKSWAGEN, Volkswagen", "AUDI, Audi",
			"MERCEDES, Mercedes-Benz", "MERCEDES BENZ, Mercedes-Benz", "SKODA, Škoda", "CITROEN, Citroën",
			"Tesla, Tesla", "YAMAHA, Yamaha", "KYMCO, KYMCO" })
	void readsTheBrandsOfTheTable(String text, String brand) {
		assertThat(vehicleService.brands().match(text)).isEqualTo(new Match(brand, true));
	}

	@ParameterizedTest(name = "{0}: {1}")
	@CsvSource(delimiter = '|', value = {
			"brand | VW | Επιλέξτε μάρκα από τη λίστα.",
			"brand | volkswagen | Επιλέξτε μάρκα από τη λίστα.",
			"category | Ι.Χ. | Επιλέξτε κατηγορία από τη λίστα.",
			"category | m1 | Επιλέξτε κατηγορία από τη λίστα.",
			"color | ΜΑΥΡΟ | Επιλέξτε χρώμα από τη λίστα.",
			"color | Λευκό-Λευκό | Επιλέξτε χρώμα από τη λίστα.",
			"emissionStandard | Euro 6d-TEMP | Επιλέξτε Euro από τη λίστα." })
	void refusesANewValueOutsideItsList(String field, String value, String message) {
		assertThatThrownBy(() -> vehicleService.create(with(valid(), field, value)))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.containsExactly(new Violation(field, message)));
		assertThat(vehicleRepository.count()).isZero();
	}

	@Test
	void takesTheListsOwnValues() {
		VehicleDto values = valid();
		values = with(values, "brand", "Mercedes-Benz");
		values = with(values, "category", "N1");
		values = with(values, "color", "Λευκό-Μαύρο");
		values = with(values, "emissionStandard", "ZEV");

		Long id = vehicleService.create(values).id();

		assertThat(vehicleRepository.findById(id)).get()
				.extracting(Vehicle::getBrand, Vehicle::getCategory, Vehicle::getColor, Vehicle::getEmissionStandard)
				.containsExactly("Mercedes-Benz", "N1", "Λευκό-Μαύρο", "ZEV");
	}

	// Task 23, decision 7: correcting the model does not first need the right colour.
	@Test
	void savesAnOldValueOutsideTheListWhileItIsLeftAsItIs() {
		Vehicle old = storedWithOldValues();
		VehicleDto values = vehicleService.find(old.getId());

		vehicleService.update(old.getId(), with(values, "model", "Golf Variant"));

		assertThat(vehicleRepository.findById(old.getId())).get()
				.extracting(Vehicle::getBrand, Vehicle::getCategory, Vehicle::getColor, Vehicle::getEmissionStandard,
						Vehicle::getModel)
				.containsExactly("VW", "Ι.Χ.", "ΛΑΔΙ", "Euro 6d-TEMP", "Golf Variant");
	}

	// A changed value must be of the list, even when it was outside it before.
	@ParameterizedTest(name = "{0}: {1}")
	@CsvSource({ "brand, V.W.", "category, Ι.Χ", "color, Λαδί", "emissionStandard, Euro 6d" })
	void refusesAChangedValueOutsideItsList(String field, String value) {
		Vehicle old = storedWithOldValues();

		assertThatThrownBy(() -> vehicleService.update(old.getId(), with(vehicleService.find(old.getId()), field,
				value)))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.extracting(Violation::field).containsExactly(field));
	}

	@Test
	void replacesAnOldValueWithOneOfTheList() {
		Vehicle old = storedWithOldValues();

		vehicleService.update(old.getId(), with(vehicleService.find(old.getId()), "category", "N1"));

		assertThat(vehicleRepository.findById(old.getId())).get().extracting(Vehicle::getCategory).isEqualTo("N1");
	}

	// Task 23b, decision 3: the form's two colours, one column.
	@Test
	void storesTwoColoursInOneColumnAndGivesThemBackAsTwoChoices() {
		Long id = vehicleService.create(with(with(valid(), "color", "Λευκό"), "secondColor", "Μαύρο")).id();

		assertThat(vehicleRepository.findById(id)).get().extracting(Vehicle::getColor).isEqualTo("Λευκό-Μαύρο");
		assertThat(vehicleService.find(id)).extracting(VehicleDto::color, VehicleDto::secondColor)
				.containsExactly("Λευκό", "Μαύρο");
		// The card shows it as stored.
		assertThat(vehicleService.findDetail(id).vehicle()).extracting(VehicleDto::color, VehicleDto::secondColor)
				.containsExactly("Λευκό-Μαύρο", null);
	}

	@ParameterizedTest(name = "{0} + {1}")
	@CsvSource(delimiter = '|', value = {
			"Λευκό | Λευκό | Το δεύτερο χρώμα πρέπει να είναι άλλο από το πρώτο.",
			"Πολύχρωμο | Μαύρο | Το «Πολύχρωμο» δεν έχει δεύτερο χρώμα.",
			"Λευκό | Πολύχρωμο | Επιλέξτε δεύτερο χρώμα από τη λίστα.",
			"Λευκό | ΜΑΥΡΟ | Επιλέξτε δεύτερο χρώμα από τη λίστα." })
	void refusesASecondColourThatCannotGoWithTheFirst(String first, String second, String message) {
		assertThatThrownBy(() -> vehicleService.create(with(with(valid(), "color", first), "secondColor", second)))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.containsExactly(new Violation("secondColor", message)));
	}

	// An old colour is kept only as it is: with a second one it is a new
	// value, and must be of the list.
	@Test
	void refusesASecondColourAfterAnOldOne() {
		Vehicle old = storedWithOldValues();

		assertThatThrownBy(() -> vehicleService.update(old.getId(), with(vehicleService.find(old.getId()),
				"secondColor", "Μαύρο")))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.containsExactly(new Violation("color", "Επιλέξτε χρώμα από τη λίστα.")));
	}

	// A pair outside the list stays whole, as the form's first choice.
	@Test
	void keepsAnOldPairOutsideTheListWhole() {
		Vehicle old = storedWithOldValues();
		old.setColor("Λευκό-ΛΑΔΙ");
		vehicleRepository.saveAndFlush(old);

		VehicleDto values = vehicleService.find(old.getId());
		assertThat(values).extracting(VehicleDto::color, VehicleDto::secondColor).containsExactly("Λευκό-ΛΑΔΙ", null);

		vehicleService.update(old.getId(), values);
		assertThat(vehicleRepository.findById(old.getId())).get().extracting(Vehicle::getColor)
				.isEqualTo("Λευκό-ΛΑΔΙ");
	}

	// The form's lists (Task 23b): the brands in the alphabet's order, and
	// the models stored, by brand.
	@Test
	void givesTheFormTheBrandsAndTheModelsStored() {
		vehicleService.create(valid());
		vehicleService.create(new VehicleDto(null, "WVWZZZ1KZAW654321", "ΑΒΕ-4321", "Volkswagen", "Polo",
				LocalDate.of(2015, 1, 1), null, "M1", "ΕΙΧ", "Μπλε", null, (short) 5, 1200, new BigDecimal("60"),
				"ΒΕΝΖΙΝΗ", null, null, null, null, null, null, null, null));

		assertThat(vehicleService.brandNames()).hasSize(100).startsWith("Abarth", "Aixam", "Alfa Romeo")
				.containsSubsequence("Saab", "SEAT", "Škoda", "Smart");
		assertThat(vehicleService.modelsByBrand()).containsExactly(
				entry("Volkswagen", List.of("Golf", "Polo")));
	}

	@ParameterizedTest
	@ValueSource(shorts = { 0, 100, -1 })
	void refusesSeatsOutsideOneToNinetyNine(short seats) {
		assertThatThrownBy(() -> vehicleService.create(with(valid(), "seats", seats)))
				.isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getViolations())
						.containsExactly(new Violation("seats", "Οι θέσεις πρέπει να είναι από 1 έως 99.")));
	}

	@ParameterizedTest
	@ValueSource(shorts = { 1, 99 })
	void savesSeatsFromOneToNinetyNine(short seats) {
		Long id = vehicleService.create(with(valid(), "seats", seats)).id();

		assertThat(vehicleRepository.findById(id)).get().extracting(Vehicle::getSeats).isEqualTo(seats);
	}

	// As the import or the development database left it, before the lists.
	private Vehicle storedWithOldValues() {
		Vehicle vehicle = new Vehicle();
		vehicle.setVin("WVWZZZ1KZAW123456");
		vehicle.setPlate("ΑΒΕ-1234");
		vehicle.setBrand("VW");
		vehicle.setModel("Golf");
		vehicle.setFirstRegistration(LocalDate.of(2012, 5, 14));
		vehicle.setCategory("Ι.Χ.");
		vehicle.setUsageType(UsageType.ΕΙΧ);
		vehicle.setColor("ΛΑΔΙ");
		vehicle.setEngineCc(1598);
		vehicle.setPowerKw(new BigDecimal("81"));
		vehicle.setFuelType(FuelType.ΒΕΝΖΙΝΗ);
		vehicle.setEmissionStandard("Euro 6d-TEMP");
		return vehicleRepository.saveAndFlush(vehicle);
	}

	private static VehicleDto valid() {
		return new VehicleDto(null, "WVWZZZ1KZAW123456", "ΑΒΕ-1234", "Volkswagen", "Golf", LocalDate.of(2012, 5, 14),
				null, "M1", "ΕΙΧ", "Λευκό", null, (short) 5, 1598, new BigDecimal("81"), "ΒΕΝΖΙΝΗ", null, null, "Euro 6",
				null, null, null, null, null);
	}

	// The same values with one field changed.
	private static VehicleDto with(VehicleDto v, String field, Object value) {
		return new VehicleDto(v.id(), v.vin(), v.plate(),
				field.equals("brand") ? (String) value : v.brand(),
				field.equals("model") ? (String) value : v.model(),
				v.firstRegistration(), v.licenseIssueDate(),
				field.equals("category") ? (String) value : v.category(),
				v.usageType(),
				field.equals("color") ? (String) value : v.color(),
				field.equals("secondColor") ? (String) value : v.secondColor(),
				field.equals("seats") ? (Short) value : v.seats(),
				v.engineCc(), v.powerKw(), v.fuelType(), v.engineNumber(), v.co2(),
				field.equals("emissionStandard") ? (String) value : v.emissionStandard(),
				v.weightKg(), v.licenseStreet(), v.licenseCity(), v.licensePostalCode(), v.version());
	}

	private void truncateTables() {
		jdbcTemplate.execute(
				"TRUNCATE audit_log, ownership, policy, vehicle, intermediary, customer RESTART IDENTITY CASCADE");
	}

}
