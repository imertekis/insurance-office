package gr.insuranceoffice.service;

import java.lang.reflect.Field;

import jakarta.persistence.Column;

/**
 * How long a text may be before the database refuses it (Task 28). The
 * limits are the entities' own {@code @Column(length = …)}, read here, so
 * that the forms' services and the Excel import check against one place.
 * {@code ColumnLimitsTest} keeps them equal to the database: Hibernate's
 * schema validation checks column types, not lengths.
 * <p>
 * For {@code VARCHAR} columns only. {@code customer.notes} is {@code TEXT},
 * has no limit, and is never checked; its {@code @Column} carries JPA's
 * default length, which means nothing for it.
 */
public final class ColumnLimits {

	private ColumnLimits() {
	}

	/**
	 * @param entity the entity class, e.g. {@code Customer.class}
	 * @param field  the name of one of its text fields, e.g. {@code "lastName"}
	 * @return the length of the column the field is stored in
	 * @throws IllegalArgumentException if the entity has no such text field: a bug
	 */
	public static int of(Class<?> entity, String field) {
		try {
			Field declared = entity.getDeclaredField(field);
			Column column = declared.getAnnotation(Column.class);
			if (column == null || declared.getType() != String.class) {
				throw new IllegalArgumentException(entity.getSimpleName() + "." + field + " is not a text column");
			}
			return column.length();
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException(entity.getSimpleName() + " has no field " + field, e);
		}
	}

	/**
	 * The length of a text as PostgreSQL counts it for {@code VARCHAR(n)}: in
	 * characters, not bytes, so 100 Greek letters fit in 100. Counted in code
	 * points, so a character outside the BMP counts as one, as in the database,
	 * although Java holds it as two {@code char}s.
	 */
	public static int length(String text) {
		return text.codePointCount(0, text.length());
	}

}
