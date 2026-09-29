package gr.insuranceoffice.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.VehicleDto;
import gr.insuranceoffice.entity.Customer.EntityType;
import gr.insuranceoffice.entity.Policy.SurchargeType;
import gr.insuranceoffice.entity.Vehicle.FuelType;
import gr.insuranceoffice.entity.Vehicle.UsageType;
import gr.insuranceoffice.service.CustomerService;
import gr.insuranceoffice.util.TextNormalizationUtils;

/**
 * The demo's synthetic data (Task 39b), as a plan that {@link DemoSeeder}
 * saves through the services. The same seed gives the same people, vehicles
 * and policies on every run, so the pictures of the README (Task 39c) show
 * the same data; the dates are counted from the day it runs, so the home
 * screen always has renewals to do.
 * <p>
 * Nothing here is anyone's (CLAUDE.md, the repository is public):
 * <ul>
 * <li>ΑΦΜ with a valid check digit, from the synthetic series 90000xxxx;
 * mobiles from 6900000xxx; email addresses at example.com. Both series are
 * the tests' own (.gitleaks.toml).</li>
 * <li>Plates whose number starts with 0, «ΙΚΒ0427»: three look-alike letters
 * and four digits, as a standard plate, but a number no real plate has, as
 * they run from 1000 to 9999. A plate from the real range would be some
 * real vehicle's.</li>
 * <li>VINs of the tests' kind: SYN, eight characters and a serial that
 * counts up.</li>
 * <li>Insurance companies named after Microsoft's sample companies, as in
 * the tests (Task 39a); intermediaries and customers made of common Greek
 * names, and cities all over Greece, so nothing points to one office.</li>
 * </ul>
 */
final class DemoData {

	/** The seed of every run. */
	static final long SEED = 39L;

	static final int CUSTOMERS = 200;

	/** How many customers own one, two and three vehicles, in that order. */
	private static final int ONE_VEHICLE = 186;
	private static final int TWO_VEHICLES = 40;
	private static final int THREE_VEHICLES = 10;

	private static final int WITHOUT_TAX_ID = 12;
	private static final int WITHOUT_MOBILE = 12;
	private static final int SHARED_VEHICLES = 15;
	private static final int TAXIS = 2;

	/** The home screen's periods (SPEC §7.1), filled by the first vehicles of the shuffled list. */
	private static final int ENDING_IN_7_DAYS = 7;
	private static final int ENDING_IN_30_DAYS = 18;
	private static final int EXPIRED_NOT_RENEWED = 12;
	private static final int LOST_LONG_AGO = 6;

	/**
	 * One intermediary. No registry number or phone: only an address at
	 * example.com, so none can be anyone's.
	 */
	record IntermediaryPlan(String fullName, String email) {
	}

	/**
	 * A vehicle's owners from a transfer date on; customers by their index in
	 * {@link Plan#customers()}, the primary owner first.
	 */
	record Owners(LocalDate transferDate, List<Integer> customers, List<String> percentages) {
	}

	/**
	 * One policy, as the form would send it.
	 *
	 * @param intermediary its index in {@link Plan#intermediaries()}, or null
	 * @param surchargeType null for none
	 */
	record PolicyPlan(String policyNumber, String insuranceCompany, Integer intermediary, LocalDate startDate,
			LocalDate endDate, String premium, String surchargeType) {
	}

	/** A vehicle, its owners in the order of their transfer dates, and its policies from the oldest on. */
	record VehiclePlan(VehicleDto vehicle, List<Owners> owners, List<PolicyPlan> policies) {
	}

	record Plan(List<IntermediaryPlan> intermediaries, List<CustomerDto> customers, List<VehiclePlan> vehicles) {
	}

	/** The plan of the given seed, its dates counted from {@code today}. */
	static Plan plan(long seed, LocalDate today) {
		return new DemoData(new Random(seed), today).plan();
	}

	// ------------------------------------------------------------ the lists

	private static final List<String> MALE_NAMES = List.of("Γεώργιος", "Ιωάννης", "Κωνσταντίνος", "Δημήτριος",
			"Νικόλαος", "Παναγιώτης", "Βασίλειος", "Χρήστος", "Αθανάσιος", "Μιχαήλ", "Ευάγγελος", "Σπυρίδων",
			"Αντώνιος", "Αναστάσιος", "Θεόδωρος", "Ανδρέας", "Χαράλαμπος", "Αλέξανδρος", "Εμμανουήλ", "Ηλίας",
			"Σταύρος", "Πέτρος", "Στυλιανός", "Απόστολος", "Φώτιος", "Διονύσιος", "Άγγελος", "Στέφανος",
			"Λεωνίδας", "Αριστείδης", "Μάριος", "Φίλιππος", "Οδυσσέας", "Ορέστης", "Θεοφάνης");

	private static final List<String> FEMALE_NAMES = List.of("Μαρία", "Ελένη", "Αικατερίνη", "Βασιλική", "Σοφία",
			"Αγγελική", "Γεωργία", "Δήμητρα", "Κωνσταντίνα", "Παρασκευή", "Αναστασία", "Χριστίνα", "Ευαγγελία",
			"Ιωάννα", "Ειρήνη", "Παναγιώτα", "Θεοδώρα", "Αλεξάνδρα", "Νικολέτα", "Ευτυχία", "Χρυσούλα", "Σταυρούλα",
			"Αθηνά", "Φωτεινή", "Δέσποινα", "Μαρίνα", "Αντωνία", "Ζωή", "Κυριακή", "Μαργαρίτα", "Στυλιανή", "Άννα",
			"Ολυμπία", "Βαρβάρα", "Ευδοκία");

	/**
	 * Each surname as a man and as a woman writes it: «Παπαδόπουλος
	 * Παπαδοπούλου». One word where both write it the same.
	 */
	private static final List<String> SURNAMES = List.of("Παπαδόπουλος Παπαδοπούλου", "Παπαγεωργίου",
			"Οικονόμου", "Γεωργίου", "Νικολάου", "Βασιλείου", "Κωνσταντίνου", "Δημητρίου", "Ιωάννου",
			"Παπανικολάου", "Αντωνίου", "Μακρής Μακρή", "Παπαδάκης Παπαδάκη", "Βλάχος Βλάχου", "Αλεξίου",
			"Αθανασίου", "Καραγιάννης Καραγιάννη", "Μαυρίδης Μαυρίδου", "Παππάς Παππά",
			"Αγγελόπουλος Αγγελοπούλου", "Νικολαΐδης Νικολαΐδου", "Χριστοδούλου", "Ευαγγέλου",
			"Σταθόπουλος Σταθοπούλου", "Κυριακίδης Κυριακίδου", "Πετρόπουλος Πετροπούλου", "Αναστασίου",
			"Λαμπρόπουλος Λαμπροπούλου", "Μιχαηλίδης Μιχαηλίδου", "Θεοδωρόπουλος Θεοδωροπούλου", "Ζαχαρίου",
			"Καλογεράκης Καλογεράκη", "Σπυρόπουλος Σπυροπούλου", "Τσακίρης Τσακίρη", "Δημόπουλος Δημοπούλου",
			"Γιαννόπουλος Γιαννοπούλου", "Αρβανίτης Αρβανίτη", "Κωστόπουλος Κωστοπούλου", "Χατζηδάκης Χατζηδάκη",
			"Μπακάλης Μπακάλη", "Σαββίδης Σαββίδου", "Φραγκιαδάκης Φραγκιαδάκη", "Ρήγας Ρήγα", "Καρράς Καρρά",
			"Λιάπης Λιάπη", "Τζαννετάκης Τζαννετάκη", "Μαργαρίτης Μαργαρίτη", "Στεφανίδης Στεφανίδου",
			"Σιδηρόπουλος Σιδηροπούλου", "Ηλιόπουλος Ηλιοπούλου", "Παναγιωτόπουλος Παναγιωτοπούλου",
			"Ξανθόπουλος Ξανθοπούλου", "Αλεξόπουλος Αλεξοπούλου", "Κατσαρός Κατσαρού", "Μανωλάκης Μανωλάκη",
			"Δρακόπουλος Δρακοπούλου", "Βουλγαράκης Βουλγαράκη", "Σαρρής Σαρρή", "Φωτίου", "Χριστοφόρου",
			"Τσολάκης Τσολάκη", "Ζερβός Ζερβού", "Πανταζής Πανταζή", "Κοντός Κοντού", "Μελετίου", "Γκίκας Γκίκα",
			"Λάμπρου", "Μουρατίδης Μουρατίδου", "Δούκας Δούκα", "Καλαϊτζής Καλαϊτζή");

	private record City(String name, String taxOffice, List<String> postalCodes) {
	}

	private static final List<City> CITIES = List.of(
			new City("Αθήνα", "Α' Αθηνών", List.of("10434", "11141", "11252", "11362", "11473", "11524", "11633")),
			new City("Πειραιάς", "Α' Πειραιά", List.of("18531", "18533", "18534", "18536", "18537")),
			new City("Θεσσαλονίκη", "Δ' Θεσσαλονίκης", List.of("54621", "54622", "54635", "54640", "54645")),
			new City("Πάτρα", "Α' Πατρών", List.of("26221", "26222", "26223", "26225")),
			new City("Ηράκλειο", "Ηρακλείου", List.of("71201", "71202", "71303", "71305")),
			new City("Λάρισα", "Α' Λάρισας", List.of("41221", "41222", "41334", "41335")),
			new City("Βόλος", "Βόλου", List.of("38221", "38222", "38333")),
			new City("Ιωάννινα", "Ιωαννίνων", List.of("45221", "45332", "45444")),
			new City("Καλαμάτα", "Καλαμάτας", List.of("24100", "24131", "24133")),
			new City("Χανιά", "Χανίων", List.of("73100", "73131", "73134")),
			new City("Καβάλα", "Καβάλας", List.of("65201", "65302", "65403")),
			new City("Ρόδος", "Ρόδου", List.of("85100", "85131", "85133")));

	private static final List<String> STREETS = List.of("Ελευθερίου Βενιζέλου", "Αγίου Δημητρίου", "Παπαφλέσσα",
			"Κολοκοτρώνη", "28ης Οκτωβρίου", "Εθνικής Αντιστάσεως", "Αγίας Σοφίας", "Μεγάλου Αλεξάνδρου",
			"Αριστοτέλους", "Πλάτωνος", "Ερμού", "Κύπρου", "Ηρώων Πολυτεχνείου", "Μακεδονίας", "Κανάρη", "Μιαούλη",
			"Καραϊσκάκη", "Ανδρούτσου", "Μπουμπουλίνας", "Αγίου Νικολάου", "Ομήρου", "Σωκράτους", "Περικλέους",
			"Θησέως", "Ιπποκράτους");

	private static final List<String> NOTES = List.of("Προτιμά επικοινωνία με email.",
			"Ενημέρωση για την ανανέωση με SMS.", "Ήρθε με σύσταση άλλου πελάτη.",
			"Ζήτησε προσφορά και για ασφάλεια κατοικίας.");

	/** Microsoft's sample companies, as in the tests (Task 39a), by share of the vehicles. */
	private static final List<String> INSURANCE_COMPANIES = List.of("Northwind Ασφαλιστική",
			"Northwind Ασφαλιστική", "Northwind Ασφαλιστική", "Contoso Ασφαλιστική", "Contoso Ασφαλιστική",
			"Fabrikam Ασφαλιστική", "Fabrikam Ασφαλιστική", "Woodgrove Ασφαλιστική", "Tailspin Ασφαλιστική");

	private static final List<IntermediaryPlan> INTERMEDIARIES = List.of(
			new IntermediaryPlan("Αναστασίου Ελένη", "e.anastasiou@example.com"),
			new IntermediaryPlan("Μαυρίδης Παναγιώτης", "p.mavridis@example.com"),
			new IntermediaryPlan("Σταθοπούλου Δήμητρα", "d.stathopoulou@example.com"),
			new IntermediaryPlan("Κυριακίδης Αντώνιος", "a.kyriakidis@example.com"));

	/**
	 * One model as its licence describes it.
	 *
	 * @param since the first year it is registered, so that no vehicle is older than its model
	 * @param engineCc null for an electric one (DATA_MODEL)
	 */
	private record Model(String brand, String model, String category, FuelType fuel, Integer engineCc,
			String powerKw, int seats, int co2, int weightKg, int since) {
	}

	private static Model car(String brand, String model, FuelType fuel, Integer engineCc, String powerKw, int co2,
			int weightKg, int since) {
		return new Model(brand, model, "M1", fuel, engineCc, powerKw, 5, co2, weightKg, since);
	}

	private static final List<Model> CARS = List.of(
			car("Toyota", "Yaris", FuelType.ΒΕΝΖΙΝΗ, 998, "53", 115, 1000, 2006),
			car("Toyota", "Yaris Hybrid", FuelType.ΥΒΡΙΔΙΚΟ, 1490, "85", 92, 1100, 2012),
			car("Toyota", "Corolla", FuelType.ΥΒΡΙΔΙΚΟ, 1798, "90", 101, 1350, 2019),
			car("Toyota", "Aygo X", FuelType.ΒΕΝΖΙΝΗ, 998, "53", 110, 940, 2022),
			car("Fiat", "Panda", FuelType.ΒΕΝΖΙΝΗ, 1242, "51", 125, 940, 2004),
			car("Fiat", "Panda", FuelType.CNG, 875, "59", 86, 1030, 2013),
			new Model("Fiat", "500", "M1", FuelType.ΒΕΝΖΙΝΗ, 1242, "51", 4, 119, 910, 2008),
			car("Volkswagen", "Polo", FuelType.ΒΕΝΖΙΝΗ, 999, "59", 118, 1100, 2006),
			car("Volkswagen", "Golf", FuelType.ΒΕΝΖΙΝΗ, 1498, "110", 130, 1300, 2008),
			car("Volkswagen", "T-Roc", FuelType.ΒΕΝΖΙΝΗ, 999, "81", 135, 1300, 2017),
			car("Opel", "Corsa", FuelType.ΒΕΝΖΙΝΗ, 1199, "55", 120, 1050, 2007),
			car("Opel", "Astra", FuelType.ΠΕΤΡΕΛΑΙΟ, 1499, "96", 115, 1300, 2010),
			car("Peugeot", "208", FuelType.ΒΕΝΖΙΝΗ, 1199, "55", 118, 1050, 2012),
			car("Peugeot", "2008", FuelType.ΒΕΝΖΙΝΗ, 1199, "96", 130, 1200, 2013),
			car("Renault", "Clio", FuelType.ΒΕΝΖΙΝΗ, 999, "67", 118, 1100, 2006),
			car("Renault", "Captur", FuelType.ΒΕΝΖΙΝΗ, 1333, "103", 135, 1300, 2013),
			car("Hyundai", "i10", FuelType.ΒΕΝΖΙΝΗ, 998, "49", 112, 930, 2008),
			car("Hyundai", "i20", FuelType.ΒΕΝΖΙΝΗ, 998, "74", 118, 1100, 2009),
			car("Kia", "Picanto", FuelType.ΒΕΝΖΙΝΗ, 998, "49", 110, 950, 2006),
			car("Kia", "Sportage", FuelType.ΠΕΤΡΕΛΑΙΟ, 1598, "100", 145, 1600, 2010),
			car("Nissan", "Micra", FuelType.ΒΕΝΖΙΝΗ, 999, "68", 120, 1050, 2006),
			car("Nissan", "Qashqai", FuelType.ΒΕΝΖΙΝΗ, 1332, "103", 140, 1400, 2008),
			car("Suzuki", "Swift", FuelType.ΒΕΝΖΙΝΗ, 1197, "61", 110, 900, 2006),
			car("Suzuki", "Vitara", FuelType.ΥΒΡΙΔΙΚΟ, 1373, "95", 125, 1250, 2015),
			car("Citroën", "C3", FuelType.ΒΕΝΖΙΝΗ, 1199, "61", 118, 1050, 2006),
			car("Dacia", "Sandero", FuelType.ΒΕΝΖΙΝΗ, 999, "67", 120, 1050, 2009),
			car("Dacia", "Sandero", FuelType.LPG, 999, "74", 108, 1100, 2017),
			car("Dacia", "Duster", FuelType.ΠΕΤΡΕΛΑΙΟ, 1461, "85", 125, 1300, 2010),
			car("Škoda", "Octavia", FuelType.ΠΕΤΡΕΛΑΙΟ, 1968, "110", 120, 1400, 2006),
			car("Škoda", "Fabia", FuelType.ΒΕΝΖΙΝΗ, 999, "70", 115, 1100, 2007),
			car("SEAT", "Ibiza", FuelType.ΒΕΝΖΙΝΗ, 999, "70", 118, 1100, 2006),
			car("Mercedes-Benz", "A 180", FuelType.ΒΕΝΖΙΝΗ, 1332, "100", 135, 1400, 2012),
			car("BMW", "118i", FuelType.ΒΕΝΖΙΝΗ, 1499, "100", 135, 1400, 2011),
			new Model("MINI", "Cooper", "M1", FuelType.ΒΕΝΖΙΝΗ, 1499, "100", 4, 130, 1250, 2006),
			car("Ford", "Fiesta", FuelType.ΒΕΝΖΙΝΗ, 999, "74", 115, 1100, 2006),
			car("Ford", "Puma", FuelType.ΒΕΝΖΙΝΗ, 999, "92", 125, 1250, 2019));

	private static final List<Model> ELECTRIC_CARS = List.of(
			car("Peugeot", "e-208", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "100", 0, 1450, 2019),
			car("Renault", "Zoe", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "80", 0, 1500, 2013),
			car("Hyundai", "Kona Electric", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "150", 0, 1700, 2018),
			car("Nissan", "Leaf", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "110", 0, 1600, 2011),
			car("Tesla", "Model 3", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "208", 0, 1800, 2019),
			car("Tesla", "Model Y", FuelType.ΗΛΕΚΤΡΙΣΜΟΣ, null, "220", 0, 2000, 2021));

	private static final List<Model> VANS = List.of(
			new Model("Volkswagen", "Caddy", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 1968, "75", 2, 150, 1600, 2006),
			new Model("Citroën", "Berlingo", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 1499, "75", 3, 140, 1500, 2008),
			new Model("Peugeot", "Partner", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 1499, "75", 3, 140, 1500, 2008),
			new Model("Ford", "Transit Custom", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 1995, "96", 3, 180, 2000, 2013),
			new Model("Fiat", "Doblo", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 1598, "77", 2, 145, 1500, 2010),
			new Model("Toyota", "Hilux", "N1", FuelType.ΠΕΤΡΕΛΑΙΟ, 2393, "110", 5, 230, 2100, 2006),
			new Model("Mercedes-Benz", "Sprinter", "N2", FuelType.ΠΕΤΡΕΛΑΙΟ, 2143, "105", 3, 220, 2500, 2006));

	private static Model motorcycle(String brand, String model, int engineCc, String powerKw, int co2,
			int weightKg, int since) {
		return new Model(brand, model, "L3e", FuelType.ΒΕΝΖΙΝΗ, engineCc, powerKw, 2, co2, weightKg, since);
	}

	private static final List<Model> MOTORCYCLES = List.of(
			motorcycle("Honda", "PCX 125", 125, "9", 45, 130, 2010),
			motorcycle("Yamaha", "NMAX 125", 125, "9", 50, 131, 2015),
			motorcycle("SYM", "Symphony 125", 125, "7", 50, 120, 2009),
			motorcycle("Piaggio", "Liberty 125", 124, "8", 50, 120, 2009),
			motorcycle("KYMCO", "Agility 125", 125, "7", 52, 115, 2008),
			motorcycle("Vespa", "Primavera 125", 125, "8", 50, 115, 2014),
			motorcycle("Kawasaki", "Z650", 649, "50", 110, 190, 2017),
			motorcycle("Honda", "Africa Twin", 1084, "75", 110, 230, 2016),
			motorcycle("BMW", "R 1250 GS", 1254, "100", 125, 250, 2019));

	private static final List<Model> TAXI_MODELS = List.of(
			car("Škoda", "Octavia", FuelType.ΠΕΤΡΕΛΑΙΟ, 1968, "110", 120, 1400, 2006),
			car("Toyota", "Corolla", FuelType.ΥΒΡΙΔΙΚΟ, 1798, "90", 101, 1350, 2019));

	/** By share of the vehicles, as colours are on the roads. */
	private static final List<String> COLORS = List.of("Λευκό", "Λευκό", "Λευκό", "Λευκό", "Γκρι", "Γκρι",
			"Γκρι", "Μαύρο", "Μαύρο", "Ασημί", "Ασημί", "Μπλε", "Μπλε", "Κόκκινο", "Κίτρινο", "Πράσινο", "Καφέ",
			"Μπορντό", "Μπεζ", "Πορτοκαλί");

	/** The 14 letters Greek and Latin share (CLAUDE.md, resolved conflict 5), as a standard plate has. */
	private static final String PLATE_LETTERS = "ΑΒΕΖΗΙΚΜΝΟΡΤΥΧ";

	/** SPEC §8: I, O and Q never appear in a VIN. */
	private static final String VIN_CHARACTERS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

	private static final Map<Character, String> LATIN = Map.ofEntries(Map.entry('Α', "a"), Map.entry('Β', "v"),
			Map.entry('Γ', "g"), Map.entry('Δ', "d"), Map.entry('Ε', "e"), Map.entry('Ζ', "z"), Map.entry('Η', "i"),
			Map.entry('Θ', "th"), Map.entry('Ι', "i"), Map.entry('Κ', "k"), Map.entry('Λ', "l"), Map.entry('Μ', "m"),
			Map.entry('Ν', "n"), Map.entry('Ξ', "x"), Map.entry('Ο', "o"), Map.entry('Π', "p"), Map.entry('Ρ', "r"),
			Map.entry('Σ', "s"), Map.entry('Τ', "t"), Map.entry('Υ', "y"), Map.entry('Φ', "f"), Map.entry('Χ', "ch"),
			Map.entry('Ψ', "ps"), Map.entry('Ω', "o"));

	private static final Map<String, String> LATIN_PAIRS = Map.of("ΟΥ", "ou", "ΑΥ", "av", "ΕΥ", "ev");

	// ------------------------------------------------------------ the plan

	private final Random random;

	private final LocalDate today;

	private DemoData(Random random, LocalDate today) {
		this.random = random;
		this.today = today;
	}

	private record Person(int surname, boolean female, String firstName, String lastName, String fatherName,
			LocalDate birthDate, LocalDate licenseDate, City city, String street, String postalCode) {
	}

	/** A vehicle while it is planned: what the later steps decide is filled in as they go. */
	private static final class Draft {
		Model model;
		String usage;
		LocalDate firstRegistration;
		LocalDate licenseIssueDate;
		int months;
		/** The end dates of its policies, the latest first. */
		final List<LocalDate> ends = new ArrayList<>();
		/** The end of a renewal already made, which starts in the future; null for none. */
		LocalDate renewedUntil;
		final List<Owners> owners = new ArrayList<>();

		int primary() {
			return owners.getLast().customers().getFirst();
		}

		boolean insuredOn(LocalDate day) {
			return ends.stream().anyMatch(end -> !day.isAfter(end) && !day.isBefore(end.minusMonths(months)));
		}
	}

	private Plan plan() {
		List<Person> people = IntStream.range(0, CUSTOMERS).mapToObj(i -> person()).toList();
		List<Draft> vehicles = vehicles(people);
		Set<Integer> withoutMobile = withoutMobile(vehicles);
		List<CustomerDto> customers = customers(people, withoutMobile);
		List<VehiclePlan> plans = new ArrayList<>();
		PolicyNumbers numbers = new PolicyNumbers(between(1000, 5000));
		for (Draft draft : vehicles) {
			plans.add(new VehiclePlan(vehicleDto(draft, people.get(draft.primary())), List.copyOf(draft.owners),
					policies(draft, people.get(draft.primary()), numbers)));
		}
		return new Plan(INTERMEDIARIES, customers, plans);
	}

	private Person person() {
		int surname = random.nextInt(SURNAMES.size());
		boolean female = random.nextBoolean();
		String[] forms = SURNAMES.get(surname).split(" ");
		String lastName = female && forms.length == 2 ? forms[1] : forms[0];
		String firstName = pick(female ? FEMALE_NAMES : MALE_NAMES);
		String fatherName = chance(0.85) ? pick(MALE_NAMES) : null;
		LocalDate birthDate = today.minusYears(between(19, 84)).minusDays(random.nextInt(365));
		LocalDate licenseDate = chance(0.9)
				? earliest(birthDate.plusYears(between(18, 30)).plusDays(random.nextInt(365)), today.minusDays(30))
				: null;
		City city = pick(CITIES);
		return new Person(surname, female, firstName, lastName, fatherName, birthDate, licenseDate, city,
				pick(STREETS) + " " + between(1, 180), pick(city.postalCodes()));
	}

	/**
	 * Every vehicle with its owners and the end dates of its policies: most
	 * customers own one, some two or three, a few none; some vehicles are
	 * shared 50/50, one changed hands.
	 */
	private List<Draft> vehicles(List<Person> people) {
		List<Integer> owners = shuffled(IntStream.range(0, CUSTOMERS).boxed().toList());
		List<Integer> primaries = new ArrayList<>(owners.subList(0, ONE_VEHICLE));
		primaries.addAll(owners.subList(0, TWO_VEHICLES));
		primaries.addAll(owners.subList(0, THREE_VEHICLES));
		Collections.shuffle(primaries, random);
		List<Integer> withoutVehicle = new ArrayList<>(owners.subList(ONE_VEHICLE, CUSTOMERS));

		List<Draft> vehicles = new ArrayList<>();
		for (int i = 0; i < primaries.size(); i++) {
			Draft draft = new Draft();
			if (i < TAXIS) {
				draft.model = pick(TAXI_MODELS);
				draft.usage = UsageType.ΤΑΞΙ.name();
			} else {
				draft.model = model();
				draft.usage = draft.model.category().startsWith("N") ? UsageType.ΦΙΧ.name() : UsageType.ΕΙΧ.name();
			}
			registration(draft);
			draft.months = draft.usage.equals(UsageType.ΤΑΞΙ.name()) || chance(0.7) ? 12 : 6;
			vehicles.add(draft);
		}

		// The periods of the home screen, and the rest of the year.
		List<Integer> order = shuffled(IntStream.range(0, vehicles.size()).boxed().toList());
		int next = 0;
		for (int i = 0; i < ENDING_IN_7_DAYS; i++) {
			policyEnds(vehicles.get(order.get(next++)), today.plusDays(i == 0 ? 0 : between(1, 7)));
		}
		for (int i = 0; i < ENDING_IN_30_DAYS; i++) {
			policyEnds(vehicles.get(order.get(next++)), today.plusDays(between(8, 30)));
		}
		for (int i = 0; i < EXPIRED_NOT_RENEWED; i++) {
			policyEnds(vehicles.get(order.get(next++)), today.minusDays(between(1, 90)));
		}
		for (int i = 0; i < LOST_LONG_AGO; i++) {
			policyEnds(vehicles.get(order.get(next++)), today.minusDays(between(91, 500)));
		}
		// One renewal made in advance: the new policy starts in the future, and
		// the current one is no longer a renewal to do.
		Draft renewedEarly = vehicles.get(order.get(next++));
		policyEnds(renewedEarly, today.plusDays(between(8, 20)));
		renewedEarly.renewedUntil = renewedEarly.ends.getFirst().plusMonths(renewedEarly.months);
		List<Integer> rest = order.subList(next, order.size());
		for (; next < order.size(); next++) {
			Draft draft = vehicles.get(order.get(next));
			int days = (int) ChronoUnit.DAYS.between(today, today.plusMonths(draft.months));
			policyEnds(draft, today.plusDays(between(31, days)));
		}

		for (int i = 0; i < vehicles.size(); i++) {
			Draft draft = vehicles.get(i);
			draft.owners.add(new Owners(draft.licenseIssueDate, List.of(primaries.get(i)), List.of("100")));
		}
		shareSome(vehicles, people, withoutVehicle);
		sellOne(vehicles, rest, withoutVehicle);
		return vehicles;
	}

	private Model model() {
		double kind = random.nextDouble();
		if (kind < 0.10) {
			return pick(VANS);
		}
		if (kind < 0.22) {
			return pick(MOTORCYCLES);
		}
		return chance(0.07) ? pick(ELECTRIC_CARS) : pick(CARS);
	}

	// Mostly registered new here; some imported used, licensed here later.
	private void registration(Draft draft) {
		LocalDate oldest = latest(LocalDate.of(draft.model.since(), 1, 1), today.minusYears(18));
		draft.firstRegistration = oldest.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(oldest, today) + 1));
		draft.licenseIssueDate = chance(0.15)
				? earliest(draft.firstRegistration.plusYears(between(1, 6)).plusDays(random.nextInt(365)), today)
				: draft.firstRegistration;
	}

	/**
	 * The vehicle's policies, back to back (Task 11d: touching is not
	 * overlapping), the latest ending on {@code end}: up to five, none
	 * starting before the vehicle was licensed here. A vehicle licensed after
	 * its latest policy started was licensed that day.
	 */
	private void policyEnds(Draft draft, LocalDate end) {
		LocalDate latestStart = end.minusMonths(draft.months);
		if (draft.licenseIssueDate.isAfter(latestStart)) {
			draft.licenseIssueDate = latestStart;
			draft.firstRegistration = earliest(draft.firstRegistration, latestStart);
		}
		int wanted = pick(List.of(1, 2, 2, 3, 3, 3, 4, 4, 5));
		for (int k = 0; k < wanted; k++) {
			LocalDate e = end.minusMonths((long) draft.months * k);
			if (e.minusMonths(draft.months).isBefore(draft.licenseIssueDate)) {
				break;
			}
			draft.ends.add(e);
		}
	}

	/** Some vehicles owned 50/50: by a relative of the same surname where there is one. */
	private void shareSome(List<Draft> vehicles, List<Person> people, List<Integer> withoutVehicle) {
		List<Integer> order = shuffled(IntStream.range(0, vehicles.size()).boxed().toList());
		for (int i = 0; i < SHARED_VEHICLES; i++) {
			Draft draft = vehicles.get(order.get(i));
			int primary = draft.primary();
			List<Integer> relatives = IntStream.range(0, CUSTOMERS)
					.filter(other -> other != primary && people.get(other).surname() == people.get(primary).surname())
					.boxed().toList();
			int coOwner = relatives.isEmpty() ? withoutVehicle.removeFirst() : pick(relatives);
			Owners only = draft.owners.removeFirst();
			draft.owners.add(new Owners(only.transferDate(), List.of(primary, coOwner), List.of("50", "50")));
		}
	}

	/**
	 * One vehicle changed hands: its first owner is a former one, and the
	 * policies from the sale on are the buyer's (Task 13). Sold on the day
	 * its second policy started, so each owner has policies of their own; one
	 * whose latest policy is in force today.
	 */
	private void sellOne(List<Draft> vehicles, List<Integer> candidates, List<Integer> withoutVehicle) {
		for (int index : candidates) {
			Draft draft = vehicles.get(index);
			if (draft.ends.size() >= 3 && draft.owners.getFirst().customers().size() == 1) {
				LocalDate sold = draft.ends.get(draft.ends.size() - 2).minusMonths(draft.months);
				draft.owners.add(new Owners(sold, List.of(withoutVehicle.removeFirst()), List.of("100")));
				return;
			}
		}
		throw new IllegalStateException("No vehicle to sell");
	}

	/**
	 * DECISIONS §1: the primary owner of a vehicle insured today has a
	 * mobile. The customers without one are chosen among the others: owners
	 * of vehicles whose policy expired, co-owners, customers without a
	 * vehicle.
	 */
	private Set<Integer> withoutMobile(List<Draft> vehicles) {
		Set<Integer> needMobile = new HashSet<>();
		vehicles.stream().filter(draft -> draft.insuredOn(today)).forEach(draft -> needMobile.add(draft.primary()));
		List<Integer> others = shuffled(IntStream.range(0, CUSTOMERS).filter(i -> !needMobile.contains(i)).boxed()
				.toList());
		return Set.copyOf(others.subList(0, Math.min(WITHOUT_MOBILE, others.size())));
	}

	private List<CustomerDto> customers(List<Person> people, Set<Integer> withoutMobile) {
		List<String> taxIds = shuffled(IntStream.range(0, 1000).mapToObj(DemoData::taxId).toList());
		List<String> mobiles = shuffled(IntStream.range(0, 1000).mapToObj(n -> "6900000%03d".formatted(n)).toList());
		Set<Integer> withoutTaxId = Set.copyOf(shuffled(IntStream.range(0, CUSTOMERS).boxed().toList())
				.subList(0, WITHOUT_TAX_ID));
		Set<String> emails = new HashSet<>();
		List<CustomerDto> customers = new ArrayList<>();
		for (int i = 0; i < people.size(); i++) {
			Person person = people.get(i);
			boolean hasTaxId = !withoutTaxId.contains(i);
			String email = chance(0.6) ? email(person, emails) : null;
			String notes = chance(0.03) ? pick(NOTES) : null;
			customers.add(new CustomerDto(null, hasTaxId ? taxIds.get(i) : null, EntityType.INDIVIDUAL.name(),
					person.lastName(), person.firstName(), person.fatherName(), person.birthDate(),
					person.licenseDate(), hasTaxId ? person.city().taxOffice() : null, person.street(),
					person.city().name(), person.postalCode(), withoutMobile.contains(i) ? null : mobiles.get(i), null,
					email, notes, null));
		}
		return customers;
	}

	/** The n-th ΑΦΜ of the series 90000xxxx: its eight digits and the one check digit that fits them. */
	private static String taxId(int n) {
		String digits = "90000%03d".formatted(n);
		return IntStream.rangeClosed(0, 9).mapToObj(check -> digits + check).filter(CustomerService::isValidTaxId)
				.findFirst().orElseThrow();
	}

	private String email(Person person, Set<String> taken) {
		String name = latin(person.firstName()) + "." + latin(person.lastName());
		String email = name + "@example.com";
		for (int n = 2; !taken.add(email); n++) {
			email = name + n + "@example.com";
		}
		return email;
	}

	// «Παπαδοπούλου» -> papadopoulou, for the email addresses.
	private static String latin(String greek) {
		String text = TextNormalizationUtils.normalizeText(greek);
		StringBuilder latin = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			String pair = text.substring(i, Math.min(i + 2, text.length()));
			if (LATIN_PAIRS.containsKey(pair)) {
				latin.append(LATIN_PAIRS.get(pair));
				i++;
			} else {
				latin.append(LATIN.get(text.charAt(i)));
			}
		}
		return latin.toString();
	}

	private VehicleDto vehicleDto(Draft draft, Person owner) {
		Model model = draft.model;
		boolean twoColours = chance(0.03);
		// A taxi is yellow, as in Athens.
		String color = draft.usage.equals(UsageType.ΤΑΞΙ.name()) ? "Κίτρινο" : pick(COLORS);
		String secondColor = twoColours && !color.equals("Μαύρο") ? "Μαύρο" : null;
		return new VehicleDto(null, vin(), plate(), model.brand(), model.model(), draft.firstRegistration,
				draft.licenseIssueDate, model.category(), draft.usage, color, secondColor, (short) model.seats(),
				model.engineCc(), new BigDecimal(model.powerKw()), model.fuel().name(), null, model.co2(),
				emissionStandard(model, draft.firstRegistration), model.weightKg(), owner.street(),
				owner.city().name(), owner.postalCode(), null);
	}

	private static String emissionStandard(Model model, LocalDate firstRegistration) {
		if (model.fuel() == FuelType.ΗΛΕΚΤΡΙΣΜΟΣ) {
			return "ZEV";
		}
		int year = firstRegistration.getYear();
		return year >= 2016 ? "Euro 6" : year >= 2011 ? "Euro 5" : year >= 2006 ? "Euro 4" : "Euro 3";
	}

	private final Set<String> plates = new HashSet<>();

	private String plate() {
		String plate;
		do {
			StringBuilder letters = new StringBuilder();
			for (int i = 0; i < 3; i++) {
				letters.append(PLATE_LETTERS.charAt(random.nextInt(PLATE_LETTERS.length())));
			}
			plate = letters + "0%03d".formatted(between(1, 999));
		} while (!plates.add(plate));
		return plate;
	}

	private int vins;

	private String vin() {
		StringBuilder vin = new StringBuilder("SYN");
		for (int i = 0; i < 8; i++) {
			vin.append(VIN_CHARACTERS.charAt(random.nextInt(VIN_CHARACTERS.length())));
		}
		return vin.append("%06d".formatted(++vins)).toString();
	}

	/** Numbers of the form the search knows, 21 and eight digits (CLAUDE.md, resolved conflict 3). */
	private final class PolicyNumbers {

		private int last;

		PolicyNumbers(int first) {
			last = first;
		}

		String next() {
			last += between(1, 40);
			return "21000%05d".formatted(last);
		}

	}

	/**
	 * The vehicle's policies from the oldest on: one company and intermediary
	 * each, sometimes another company before; the premium by kind and power,
	 * a little lower each year back; a surcharge for a young or an elderly
	 * driver.
	 */
	private List<PolicyPlan> policies(Draft draft, Person owner, PolicyNumbers numbers) {
		String company = pick(INSURANCE_COMPANIES);
		String earlierCompany = draft.ends.size() > 1 && chance(0.15) ? pick(INSURANCE_COMPANIES) : company;
		Integer intermediary = chance(0.35) ? random.nextInt(INTERMEDIARIES.size()) : null;
		BigDecimal yearly = yearlyPremium(draft);
		List<PolicyPlan> policies = new ArrayList<>();
		for (int k = draft.ends.size() - 1; k >= 0; k--) {
			LocalDate end = draft.ends.get(k);
			policies.add(policy(draft, owner, numbers.next(), k == draft.ends.size() - 1 ? earlierCompany : company,
					intermediary, end.minusMonths(draft.months), end, yearly, k));
		}
		if (draft.renewedUntil != null) {
			LocalDate start = draft.ends.getFirst();
			policies.add(policy(draft, owner, numbers.next(), company, intermediary, start, draft.renewedUntil, yearly,
					-1));
		}
		return policies;
	}

	private PolicyPlan policy(Draft draft, Person owner, String number, String company, Integer intermediary,
			LocalDate start, LocalDate end, BigDecimal yearly, int yearsBack) {
		int age = (int) ChronoUnit.YEARS.between(owner.birthDate(), start);
		String surcharge = age < 25 ? SurchargeType.ΝΕΟΣ_ΟΔΗΓΟΣ.name() : age >= 76 ? SurchargeType.ΗΛΙΚΙΑΣ.name() : null;
		BigDecimal premium = yearly.multiply(BigDecimal.valueOf(1 - 0.025 * yearsBack))
				.multiply(draft.months == 6 ? new BigDecimal("0.55") : BigDecimal.ONE)
				.multiply(surcharge != null ? new BigDecimal("1.25") : BigDecimal.ONE)
				.setScale(2, RoundingMode.HALF_UP);
		return new PolicyPlan(number, company, intermediary, start, end,
				premium.toPlainString().replace('.', ','), surcharge);
	}

	private BigDecimal yearlyPremium(Draft draft) {
		BigDecimal power = new BigDecimal(draft.model.powerKw());
		BigDecimal base;
		if (draft.usage.equals(UsageType.ΤΑΞΙ.name())) {
			base = new BigDecimal(1050);
		} else if (draft.model.category().startsWith("L")) {
			base = new BigDecimal(55).add(power.multiply(new BigDecimal("1.4")));
		} else if (draft.model.category().startsWith("N")) {
			base = new BigDecimal(240).add(power.multiply(new BigDecimal("1.8")));
		} else {
			base = new BigDecimal(120).add(power.multiply(new BigDecimal("1.6")));
		}
		return base.add(BigDecimal.valueOf(random.nextInt(8000), 2));
	}

	// ------------------------------------------------------------ helpers

	private <T> T pick(List<T> values) {
		return values.get(random.nextInt(values.size()));
	}

	private boolean chance(double probability) {
		return random.nextDouble() < probability;
	}

	/** Both ends included. */
	private int between(int from, int to) {
		return from + random.nextInt(to - from + 1);
	}

	private <T> List<T> shuffled(List<T> values) {
		List<T> copy = new ArrayList<>(values);
		Collections.shuffle(copy, random);
		return copy;
	}

	private static LocalDate earliest(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate latest(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

}
