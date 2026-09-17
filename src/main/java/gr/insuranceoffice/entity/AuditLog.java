package gr.insuranceoffice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Change history, and the only way back after a hard delete (DECISIONS §4).
 * Filled by {@link AuditListener}.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

	public enum Action {
		CREATE, UPDATE, DELETE, VIEW
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Plain id, not a @ManyToOne: the log must survive the user being deleted.
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 10)
	private Action action;

	@Column(name = "entity_type", nullable = false, length = 50)
	private String entityType;

	@Column(name = "entity_id")
	private Long entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "old_values")
	private String oldValues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "new_values")
	private String newValues;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	// "timestamp" is a SQL keyword, so the column name stays quoted.
	@CreationTimestamp
	@Column(name = "\"timestamp\"", nullable = false, updatable = false)
	private LocalDateTime timestamp;

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public void setEntityId(Long entityId) {
		this.entityId = entityId;
	}

	public String getOldValues() {
		return oldValues;
	}

	public void setOldValues(String oldValues) {
		this.oldValues = oldValues;
	}

	public String getNewValues() {
		return newValues;
	}

	public void setNewValues(String newValues) {
		this.newValues = newValues;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

}
