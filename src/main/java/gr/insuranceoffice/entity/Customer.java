package gr.insuranceoffice.entity;

import java.time.LocalDate;
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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "customer")
public class Customer {

	public enum EntityType {
		INDIVIDUAL, COMPANY
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tax_id", length = 9)
	private String taxId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 20)
	private EntityType entityType = EntityType.INDIVIDUAL;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "father_name", length = 100)
	private String fatherName;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "license_date")
	private LocalDate licenseDate;

	@Column(name = "tax_office", length = 100)
	private String taxOffice;

	@Column(name = "street", length = 200)
	private String street;

	@Column(name = "city", length = 100)
	private String city;

	@Column(name = "postal_code", length = 5)
	private String postalCode;

	@Column(name = "mobile", length = 10)
	private String mobile;

	@Column(name = "phone", length = 10)
	private String phone;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "notes")
	private String notes;

	// Generated column computed by PostgreSQL (V2 migration). Never written
	// from Java; Hibernate re-reads it after every insert and update.
	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "search_normalized", insertable = false, updatable = false)
	private String searchNormalized;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public Long getId() {
		return id;
	}

	public String getTaxId() {
		return taxId;
	}

	public void setTaxId(String taxId) {
		this.taxId = taxId;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(EntityType entityType) {
		this.entityType = entityType;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getFatherName() {
		return fatherName;
	}

	public void setFatherName(String fatherName) {
		this.fatherName = fatherName;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}

	public LocalDate getLicenseDate() {
		return licenseDate;
	}

	public void setLicenseDate(LocalDate licenseDate) {
		this.licenseDate = licenseDate;
	}

	public String getTaxOffice() {
		return taxOffice;
	}

	public void setTaxOffice(String taxOffice) {
		this.taxOffice = taxOffice;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getSearchNormalized() {
		return searchNormalized;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
