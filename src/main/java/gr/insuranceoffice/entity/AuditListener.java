package gr.insuranceoffice.entity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.type.Type;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import gr.insuranceoffice.entity.AuditLog.Action;
import gr.insuranceoffice.security.CurrentUser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes every insert, update and hard delete of an audited entity to
 * {@code audit_log}, in the same transaction as the change, so a change and
 * its log entry are committed or rolled back together (ARCHITECTURE §6).
 * <p>
 * Values are keyed by column name: a hard-deleted row can be put back from
 * {@code old_values} with {@code jsonb_populate_record} (DECISIONS §4). An
 * update records only the columns that changed. Associations are recorded as
 * their ids, collections not at all, and generated columns such as
 * {@code search_normalized} are left out because the database recomputes
 * them.
 * <p>
 * The log is written with plain JDBC on the transaction's connection: JPA
 * does not allow persisting another entity from inside a lifecycle callback.
 * Hibernate creates this listener through Spring while the
 * EntityManagerFactory is still being built, so its dependencies are looked
 * up only when a change happens.
 */
public class AuditListener {

	private static final String INSERT = """
			INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_values, new_values)
			VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
			""";

	private final ObjectProvider<EntityManagerFactory> entityManagerFactory;
	private final ObjectProvider<JdbcTemplate> jdbcTemplate;
	private final ObjectProvider<JsonMapper> jsonMapper;

	public AuditListener(ObjectProvider<EntityManagerFactory> entityManagerFactory,
			ObjectProvider<JdbcTemplate> jdbcTemplate, ObjectProvider<JsonMapper> jsonMapper) {
		this.entityManagerFactory = entityManagerFactory;
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	// After the insert, so that the generated id is known.
	@PostPersist
	void created(Object entity) {
		SharedSessionContractImplementor session = session();
		write(Action.CREATE, entity, session, null, columns(entity, session, currentState(entity, session)));
	}

	// Before the update, while the persistence context still holds the values
	// as loaded. Entity callbacks such as Vehicle's plate normalization run
	// after listeners, so a derived column like plate_normalized is not in
	// the diff; the column it is derived from is.
	@PreUpdate
	void updating(Object entity) {
		SharedSessionContractImplementor session = session();
		EntityEntry entry = session.getPersistenceContextInternal().getEntry(entity);
		Map<String, Object> before = columns(entity, session, entry.getLoadedState());
		Map<String, Object> after = columns(entity, session, currentState(entity, session));
		before.entrySet().removeIf(column -> same(column.getValue(), after.get(column.getKey())));
		after.keySet().retainAll(before.keySet());
		if (!after.isEmpty()) {
			write(Action.UPDATE, entity, session, before, after);
		}
	}

	@PreRemove
	void removing(Object entity) {
		SharedSessionContractImplementor session = session();
		write(Action.DELETE, entity, session, columns(entity, session, currentState(entity, session)), null);
	}

	private SharedSessionContractImplementor session() {
		EntityManager entityManager = EntityManagerFactoryUtils
				.getTransactionalEntityManager(entityManagerFactory.getObject());
		if (entityManager == null) {
			throw new IllegalStateException("An audited change must run inside a transaction");
		}
		return entityManager.unwrap(SharedSessionContractImplementor.class);
	}

	private static Object[] currentState(Object entity, SharedSessionContractImplementor session) {
		return session.getEntityPersister(null, entity).getValues(entity);
	}

	private Map<String, Object> columns(Object entity, SharedSessionContractImplementor session, Object[] state) {
		AbstractEntityPersister persister = (AbstractEntityPersister) session.getEntityPersister(null, entity);
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put(persister.getIdentifierColumnNames()[0], persister.getIdentifier(entity, session));
		Type[] types = persister.getPropertyTypes();
		boolean[] insertable = persister.getPropertyInsertability();
		for (int i = 0; i < types.length; i++) {
			if (types[i].isCollectionType() || !insertable[i]) {
				continue;
			}
			Object value = state[i];
			if (types[i].isEntityType() && value != null) {
				value = entityManagerFactory.getObject().getPersistenceUnitUtil().getIdentifier(value);
			}
			columns.put(persister.getPropertyColumnNames(i)[0], value);
		}
		return columns;
	}

	// 81 and 81.00 are the same amount.
	private static boolean same(Object before, Object after) {
		if (before instanceof BigDecimal a && after instanceof BigDecimal b) {
			return a.compareTo(b) == 0;
		}
		return Objects.equals(before, after);
	}

	// user_id is NULL for a change nobody is logged in for, such as the
	// one-off Excel import.
	private void write(Action action, Object entity, SharedSessionContractImplementor session,
			Map<String, Object> oldValues, Map<String, Object> newValues) {
		Object id = session.getEntityPersister(null, entity).getIdentifier(entity, session);
		jdbcTemplate.getObject().update(INSERT, CurrentUser.id().orElse(null), action.name(),
				Hibernate.getClass(entity).getSimpleName(), id, json(oldValues), json(newValues));
	}

	private String json(Map<String, Object> values) {
		return values == null ? null : jsonMapper.getObject().writeValueAsString(values);
	}

}
