package gr.insuranceoffice.importer;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import gr.insuranceoffice.service.ColumnLimits;

/**
 * One data row, with typed access to its cells by column header. A value
 * that cannot be read is recorded as an {@link ImportError} and returned as
 * {@code null}, so every problem in the row is found before deciding whether
 * to write it.
 */
final class ExcelRow {

	/** A cell as read: its text as Excel displays it, and its date if Excel stores it as one. */
	record RawCell(String text, LocalDate date) {

		static final RawCell BLANK = new RawCell("", null);

	}

	private final String file;
	private final int number;
	private final Map<String, RawCell> cells;
	private final List<ImportError> errors;
	private boolean rejected;

	ExcelRow(String file, int number, Map<String, RawCell> cells, List<ImportError> errors) {
		this.file = file;
		this.number = number;
		this.cells = cells;
		this.errors = errors;
	}

	boolean isRejected() {
		return rejected;
	}

	/** Whether the cell has a value; "-" counts as empty. */
	boolean has(String column) {
		RawCell cell = cell(column);
		return cell.date() != null || ExcelValues.blankToNull(cell.text()) != null;
	}

	String text(String column) {
		return value(column, Function.identity());
	}

	String requiredText(String column) {
		return requiredValue(column, Function.identity());
	}

	/** A text cell stored as it is in the entity's field: null too when longer than its column. */
	String text(String column, Class<?> entity, String field) {
		String text = text(column);
		return fits(column, text, entity, field) ? text : null;
	}

	String requiredText(String column, Class<?> entity, String field) {
		String text = requiredText(column);
		return fits(column, text, entity, field) ? text : null;
	}

	/**
	 * Task 28: refuses a value longer than the column the entity's field is
	 * stored in. Checked before the row is written, so the report lists every
	 * such cell of both files at once; the database would stop the import at
	 * the first one. The value is given as the entity will store it.
	 *
	 * @return whether it fits; an empty cell does
	 */
	boolean fits(String column, String value, Class<?> entity, String field) {
		if (value == null) {
			return true;
		}
		int limit = ColumnLimits.of(entity, field);
		int length = ColumnLimits.length(value);
		if (length <= limit) {
			return true;
		}
		reject(column, "έως " + limit + " χαρακτήρες (έχει " + length + ")");
		return false;
	}

	<T> T value(String column, Function<String, T> parser) {
		return parse(column, parser, false);
	}

	<T> T requiredValue(String column, Function<String, T> parser) {
		return parse(column, parser, true);
	}

	LocalDate date(String column) {
		return date(column, false);
	}

	LocalDate requiredDate(String column) {
		return date(column, true);
	}

	/** Records a problem with this row; {@code column} is null when it concerns the whole row. */
	void reject(String column, String message) {
		errors.add(new ImportError(file, number, column, message));
		rejected = true;
	}

	private LocalDate date(String column, boolean required) {
		LocalDate date = cell(column).date();
		return date != null ? date : parse(column, ExcelValues::parseDate, required);
	}

	private <T> T parse(String column, Function<String, T> parser, boolean required) {
		String text = ExcelValues.blankToNull(cell(column).text());
		if (text == null) {
			if (required) {
				reject(column, "υποχρεωτικό πεδίο");
			}
			return null;
		}
		try {
			return parser.apply(text);
		} catch (IllegalArgumentException e) {
			reject(column, e.getMessage());
			return null;
		}
	}

	private RawCell cell(String column) {
		RawCell cell = cells.get(column);
		if (cell == null) {
			// A column the importer never declared: a bug, not a problem in the file.
			throw new IllegalStateException("Undeclared column: " + column);
		}
		return cell;
	}

}
