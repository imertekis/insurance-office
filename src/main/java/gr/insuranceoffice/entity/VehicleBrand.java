package gr.insuranceoffice.entity;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A brand the vehicle form offers, with the spellings the Excel import maps
 * onto it (Task 23a). Written by migrations only (V7), until a screen for it
 * exists (Task 27), so the application only reads it and it is not audited.
 */
@Entity
@Immutable
@Table(name = "vehicle_brand")
public class VehicleBrand {

	@Id
	private Long id;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "synonyms", nullable = false)
	private List<String> synonyms;

	protected VehicleBrand() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<String> getSynonyms() {
		return synonyms;
	}

}
