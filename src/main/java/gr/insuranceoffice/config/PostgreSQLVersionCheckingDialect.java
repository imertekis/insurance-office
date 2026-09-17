package gr.insuranceoffice.config;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * PostgreSQL without {@code UPDATE ... RETURNING}, so that optimistic locking
 * works on entities with a database-generated column.
 * <p>
 * Customer and Vehicle re-read {@code search_normalized} after every update.
 * With {@code RETURNING}, an update whose {@code WHERE version = ?} matches
 * no row fails in Hibernate 7.4 with "The database returned no natively
 * generated values" instead of a stale-state error, so a concurrent change
 * would surface as a 500 rather than a 409 (SPEC §9). Without it, Hibernate
 * checks the row count first and re-reads the column with a SELECT.
 * {@code OptimisticLockingTest} fails if this dialect is not in use.
 */
public class PostgreSQLVersionCheckingDialect extends PostgreSQLDialect {

	public PostgreSQLVersionCheckingDialect() {
	}

	public PostgreSQLVersionCheckingDialect(DialectResolutionInfo info) {
		super(info);
	}

	public PostgreSQLVersionCheckingDialect(DatabaseVersion version) {
		super(version);
	}

	@Override
	public boolean supportsUpdateReturning() {
		return false;
	}

}
