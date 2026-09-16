package gr.insuranceoffice.importer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The files have problems. Carries all of them, so that every row can be
 * fixed in one go. Thrown out of the import's transaction, so nothing from
 * the failed run is kept.
 */
public class ExcelImportException extends RuntimeException {

	private final List<ImportError> errors;

	public ExcelImportException(List<ImportError> errors) {
		super(errors.stream()
				.map(ImportError::toString)
				.collect(Collectors.joining("\n", "Η εισαγωγή ακυρώθηκε, " + errors.size() + " σφάλματα:\n", "")));
		this.errors = List.copyOf(errors);
	}

	public List<ImportError> getErrors() {
		return errors;
	}

}
