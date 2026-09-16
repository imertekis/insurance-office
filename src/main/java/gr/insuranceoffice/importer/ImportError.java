package gr.insuranceoffice.importer;

/**
 * One problem in an input file, located the way the clerk finds it in Excel.
 *
 * @param file    which of the two files
 * @param row     1-based Excel row number, or 0 for a problem with the whole file
 * @param column  the column header, or {@code null} when not about one column
 * @param message what is wrong, in Greek
 */
public record ImportError(String file, int row, String column, String message) {

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(file);
		if (row > 0) {
			text.append(", γραμμή ").append(row);
		}
		if (column != null) {
			text.append(", στήλη «").append(column).append('»');
		}
		return text.append(": ").append(message).toString();
	}

}
