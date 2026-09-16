package gr.insuranceoffice.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads the first sheet of a workbook: row 1 holds the column headers and
 * every following non-blank row is data. The only class that uses Apache POI.
 */
final class ExcelSheet {

	private ExcelSheet() {
	}

	/**
	 * Returns the data rows. When the file cannot be opened or a declared
	 * header is missing, the problem is added to {@code errors} and no rows
	 * are returned, since none could be read reliably.
	 */
	static List<ExcelRow> read(InputStream input, String file, List<String> headers, List<ImportError> errors) {
		Workbook workbook;
		try {
			workbook = WorkbookFactory.create(input);
		} catch (IOException | IllegalArgumentException | EncryptedDocumentException e) {
			errors.add(new ImportError(file, 0, null, "το αρχείο δεν διαβάζεται ως Excel (" + e.getMessage() + ")"));
			return List.of();
		}
		try (workbook) {
			return read(workbook, file, headers, errors);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<ExcelRow> read(Workbook workbook, String file, List<String> headers,
			List<ImportError> errors) {
		// Greek locale, so that number cells read as the clerk sees them ("103,5").
		DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("el-GR"));
		FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
		Sheet sheet = workbook.getSheetAt(0);

		Map<String, Integer> columns = findColumns(sheet.getRow(0), headers, file, errors, formatter);
		if (columns == null) {
			return List.of();
		}

		List<ExcelRow> rows = new ArrayList<>();
		for (int index = 1; index <= sheet.getLastRowNum(); index++) {
			Row row = sheet.getRow(index);
			Map<String, ExcelRow.RawCell> cells = new HashMap<>();
			boolean blank = true;
			for (Map.Entry<String, Integer> column : columns.entrySet()) {
				Cell cell = row == null ? null : row.getCell(column.getValue());
				ExcelRow.RawCell rawCell = rawCell(cell, formatter, evaluator);
				blank &= rawCell.text().isBlank() && rawCell.date() == null;
				cells.put(column.getKey(), rawCell);
			}
			if (!blank) {
				rows.add(new ExcelRow(file, index + 1, cells, errors));
			}
		}
		return rows;
	}

	private static Map<String, Integer> findColumns(Row headerRow, List<String> headers, String file,
			List<ImportError> errors, DataFormatter formatter) {
		Map<String, Integer> indexByKey = new HashMap<>();
		if (headerRow != null) {
			for (Cell cell : headerRow) {
				indexByKey.putIfAbsent(ExcelValues.matchKey(formatter.formatCellValue(cell)), cell.getColumnIndex());
			}
		}
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (String header : headers) {
			Integer index = indexByKey.get(ExcelValues.matchKey(header));
			if (index == null) {
				errors.add(new ImportError(file, 1, header, "η στήλη λείπει από τη γραμμή επικεφαλίδων"));
			} else {
				columns.put(header, index);
			}
		}
		return columns.size() == headers.size() ? columns : null;
	}

	private static ExcelRow.RawCell rawCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
		if (cell == null) {
			return ExcelRow.RawCell.BLANK;
		}
		CellType type = cell.getCellType() == CellType.FORMULA
				? evaluator.evaluateFormulaCell(cell)
				: cell.getCellType();
		// The office's files hold dates as text, but a date retyped in Excel
		// becomes a number with a date format.
		LocalDate date = type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
				? cell.getLocalDateTimeCellValue().toLocalDate()
				: null;
		return new ExcelRow.RawCell(formatter.formatCellValue(cell, evaluator), date);
	}

}
