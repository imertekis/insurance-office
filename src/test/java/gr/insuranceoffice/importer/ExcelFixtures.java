package gr.insuranceoffice.importer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Synthetic workbooks shaped like the office's two Excel files: the same
 * headers and the same text formats ("180,00 €", "50%", "-" for empty).
 * No real customer data (CLAUDE.md): the ΑΦΜs are made up but pass the
 * checksum. Built in memory because *.xlsx files are gitignored.
 */
final class ExcelFixtures {

	static final String ALEXIOU_KONSTANTINOS_TAX_ID = "900000017";
	static final String DIMITRIOU_TAX_ID = "900000029";
	static final String OIKONOMOU_TAX_ID = "900000030";
	static final String STAVROU_TAX_ID = "900000042";
	static final String KOSTOPOULOS_TAX_ID = "900000054";
	static final String VASILEIOU_TAX_ID = "900000066";
	static final String RAFAILIDIS_TAX_ID = "900000078";
	static final String ALEXIOU_MARIA_TAX_ID = "900000080";
	/** Passes the checksum but is in neither file. */
	static final String UNKNOWN_TAX_ID = "900000091";

	static final String INTERMEDIARY = "ΔΟΚΙΜΑΣΤΙΚΟΣ ΣΥΝΕΡΓΑΤΗΣ";

	// Typed out rather than taken from the importer, so that a wrong header
	// constant in the importer fails these tests.
	static final List<String> CUSTOMER_HEADERS = List.of("Επώνυμο", "Όνομα", "Πατρώνυμο", "Οδός", "Πόλη", "Τ.Κ.",
			"Α.Φ.Μ.", "Ημερομηνία Γέννησης", "Δ.Ο.Υ.", "Κινητό Τηλέφωνο", "Σταθερό Τηλέφωνο", "Email",
			"Ημ. Απόκτησης Διπλώματος");

	static final List<String> ARCHIVE_HEADERS = List.of("Αρ. Συμβολαίου", "Αρ. Κυκλοφορίας (A)", "Επώνυμο (C.1.1)",
			"Όνομα (C.1.2)", "Οδός (C.1.3)", "Πόλη", "Τ.Κ.", "Α.Φ.Μ.", "Ποσοστό Ιδιοκτησίας (Κύριος %)",
			"Συνιδιοκτήτης (Επώνυμο - Όνομα)", "Α.Φ.Μ. Συνιδιοκτήτη", "Ποσοστό Ιδιοκτησίας (Συνιδιοκτήτης %)",
			"Μάρκα (D.1)", "Μοντέλο (D.3)", "1η Άδεια (B)", "Έκδοση (I)", "Αρ. Πλαισίου / VIN (E)", "Κατηγορία (J)",
			"Χρήση Οχήματος", "Χρώμα (R)", "Θέσεις (S.1)", "Κυβικά (P.1)", "Ισχύς kW (P.2)", "Καύσιμο (P.3)",
			"Αρ. Κινητήρα (P.5)", "CO2 (V.7)", "Euro (V.9)", "Βάρος kg (G)", "Έναρξη Ασφάλειας", "Λήξη Ασφάλειας",
			"Ασφαλιστική Εταιρεία", "Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)", "Πληρωτέα Μικτά Ασφάλιστρα",
			"Διαμεσολαβούν Πρόσωπο");

	private ExcelFixtures() {
	}

	/** Eight customers. Like in the office's file, Αλεξίου Μαρία has only her name and ΑΦΜ. */
	static List<Map<String, Object>> sampleCustomers() {
		Map<String, Object> maria = new LinkedHashMap<>();
		CUSTOMER_HEADERS.forEach(header -> maria.put(header, "-"));
		maria.put("Επώνυμο", "Αλεξίου");
		maria.put("Όνομα", "Μαρία");
		maria.put("Α.Φ.Μ.", ALEXIOU_MARIA_TAX_ID);

		return new ArrayList<>(List.of(
				customer("Αλεξίου", "Κωνσταντίνος", ALEXIOU_KONSTANTINOS_TAX_ID, "6900000001"),
				customer("Δημητρίου", "Ελένη", DIMITRIOU_TAX_ID, "6900000002"),
				customer("Οικονόμου", "Νίκος", OIKONOMOU_TAX_ID, "6900000003"),
				customer("Σταύρου", "Άννα", STAVROU_TAX_ID, "6900000004"),
				customer("Κωστόπουλος", "Πέτρος", KOSTOPOULOS_TAX_ID, "6900000005"),
				customer("Βασιλείου", "Σοφία", VASILEIOU_TAX_ID, "6900000006"),
				customer("Ραφαηλίδης", "Μιχάλης", RAFAILIDIS_TAX_ID, "6900000007"),
				maria));
	}

	/**
	 * Eight vehicles, one row each, in the shapes of the office's file:
	 * Αλεξίου Κωνσταντίνος owns two, one is co-owned 50/50 with Αλεξίου Μαρία,
	 * one is electric with 0 cc, and one policy runs for six months.
	 */
	static List<Map<String, Object>> sampleArchive() {
		Map<String, Object> ageSurcharge = vehicle("2100000001", "ΑΒΕ-1001", "SYNTHVH0000000001",
				ALEXIOU_KONSTANTINOS_TAX_ID);
		ageSurcharge.put("Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)", "ΝΑΙ (Ε.Η.)");
		ageSurcharge.put("Πληρωτέα Μικτά Ασφάλιστρα", "180,00 €");

		Map<String, Object> newDriverSurcharge = vehicle("2100000002", "TST-1002", "SYNTHVH0000000002",
				ALEXIOU_KONSTANTINOS_TAX_ID);
		newDriverSurcharge.put("Καύσιμο (P.3)", "ΥΒΡΙΔΙΚΟ");
		newDriverSurcharge.put("Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)", "ΝΑΙ (Ν.Ο.Δ.)");

		Map<String, Object> coOwned = vehicle("2100000004", "TST-1004", "SYNTHVH0000000004", OIKONOMOU_TAX_ID);
		coOwned.put("Ποσοστό Ιδιοκτησίας (Κύριος %)", "50%");
		coOwned.put("Συνιδιοκτήτης (Επώνυμο - Όνομα)", "Αλεξίου Μαρία");
		coOwned.put("Α.Φ.Μ. Συνιδιοκτήτη", ALEXIOU_MARIA_TAX_ID);
		coOwned.put("Ποσοστό Ιδιοκτησίας (Συνιδιοκτήτης %)", "50%");
		coOwned.put("Καύσιμο (P.3)", "ΠΕΤΡΕΛΑΙΟ");

		Map<String, Object> sixMonths = vehicle("2100000006", "TST-1006", "SYNTHVH0000000006", KOSTOPOULOS_TAX_ID);
		sixMonths.put("Έναρξη Ασφάλειας", "26/03/2026");
		sixMonths.put("Λήξη Ασφάλειας", "26/09/2026");

		Map<String, Object> electric = vehicle("2100000008", "TST-1008", "SYNTHVH0000000008", RAFAILIDIS_TAX_ID);
		electric.put("Μάρκα (D.1)", "Tesla");
		electric.put("Μοντέλο (D.3)", "Model 3");
		electric.put("Καύσιμο (P.3)", "ΗΛΕΚΤΡΙΣΜΟΣ");
		electric.put("Κυβικά (P.1)", "0");
		electric.put("CO2 (V.7)", "0");
		electric.put("Euro (V.9)", "ZEV");

		return new ArrayList<>(List.of(
				ageSurcharge,
				newDriverSurcharge,
				vehicle("2100000003", "TST-1003", "SYNTHVH0000000003", DIMITRIOU_TAX_ID),
				coOwned,
				vehicle("2100000005", "TST-1005", "SYNTHVH0000000005", STAVROU_TAX_ID),
				sixMonths,
				vehicle("2100000007", "TST-1007", "SYNTHVH0000000007", VASILEIOU_TAX_ID),
				electric));
	}

	/**
	 * Writes the rows as a one-sheet workbook. Strings become text cells, as
	 * in the office's files; dates and numbers become real Excel cells. A
	 * header missing from a row is written as "-".
	 */
	static InputStream workbook(List<String> headers, List<Map<String, Object>> rows) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Δεδομένα");
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

			Row headerRow = sheet.createRow(0);
			for (int column = 0; column < headers.size(); column++) {
				headerRow.createCell(column).setCellValue(headers.get(column));
			}
			for (int index = 0; index < rows.size(); index++) {
				Row row = sheet.createRow(index + 1);
				for (int column = 0; column < headers.size(); column++) {
					Cell cell = row.createCell(column);
					switch (rows.get(index).getOrDefault(headers.get(column), "-")) {
						case LocalDate date -> {
							cell.setCellValue(date);
							cell.setCellStyle(dateStyle);
						}
						case Number number -> cell.setCellValue(number.doubleValue());
						case Object text -> cell.setCellValue(text.toString());
					}
				}
			}
			workbook.write(out);
			return new ByteArrayInputStream(out.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<String, Object> customer(String lastName, String firstName, String taxId, String mobile) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("Επώνυμο", lastName);
		row.put("Όνομα", firstName);
		row.put("Πατρώνυμο", "Γεώργιος");
		row.put("Οδός", "Οδός Δοκιμής 1");
		row.put("Πόλη", "Δοκιμούπολη");
		row.put("Τ.Κ.", "99000");
		row.put("Α.Φ.Μ.", taxId);
		row.put("Ημερομηνία Γέννησης", "15/04/1980");
		row.put("Δ.Ο.Υ.", "Δοκιμούπολης");
		row.put("Κινητό Τηλέφωνο", mobile);
		row.put("Σταθερό Τηλέφωνο", "2990000000");
		row.put("Email", "test@example.com");
		row.put("Ημ. Απόκτησης Διπλώματος", "20/05/1999");
		return row;
	}

	// One vehicle with a single owner and a twelve-month policy.
	private static Map<String, Object> vehicle(String policyNumber, String plate, String vin, String ownerTaxId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("Αρ. Συμβολαίου", policyNumber);
		row.put("Αρ. Κυκλοφορίας (A)", plate);
		row.put("Επώνυμο (C.1.1)", "Δοκιμαστικός");
		row.put("Όνομα (C.1.2)", "Ιδιοκτήτης");
		row.put("Οδός (C.1.3)", "Οδός Άδειας 10");
		row.put("Πόλη", "Δοκιμοχώρι");
		row.put("Τ.Κ.", "99100");
		row.put("Α.Φ.Μ.", ownerTaxId);
		row.put("Ποσοστό Ιδιοκτησίας (Κύριος %)", "100%");
		row.put("Μάρκα (D.1)", "Volkswagen");
		row.put("Μοντέλο (D.3)", "Golf");
		row.put("1η Άδεια (B)", "14/05/2012");
		row.put("Έκδοση (I)", "22/11/2021");
		row.put("Αρ. Πλαισίου / VIN (E)", vin);
		row.put("Κατηγορία (J)", "M1");
		row.put("Χρήση Οχήματος", "ΕΙΧ");
		row.put("Χρώμα (R)", "Λευκό");
		row.put("Θέσεις (S.1)", "5");
		row.put("Κυβικά (P.1)", "1598");
		row.put("Ισχύς kW (P.2)", "81");
		row.put("Καύσιμο (P.3)", "ΒΕΝΖΙΝΗ");
		row.put("Αρ. Κινητήρα (P.5)", "ENG0001");
		row.put("CO2 (V.7)", "120");
		row.put("Euro (V.9)", "Euro 6");
		row.put("Βάρος kg (G)", "1250");
		row.put("Έναρξη Ασφάλειας", "01/03/2026");
		row.put("Λήξη Ασφάλειας", "01/03/2027");
		row.put("Ασφαλιστική Εταιρεία", "Δοκιμαστική Ασφαλιστική");
		row.put("Επασφάλιστρο (Νέος Οδηγός/Ηλικίας)", "ΟΧΙ");
		row.put("Πληρωτέα Μικτά Ασφάλιστρα", "150,00 €");
		row.put("Διαμεσολαβούν Πρόσωπο", INTERMEDIARY);
		return row;
	}

}
