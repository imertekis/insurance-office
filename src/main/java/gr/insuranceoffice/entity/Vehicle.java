package gr.insuranceoffice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import gr.insuranceoffice.util.TextNormalizationUtils;

@Entity
@EntityListeners(AuditListener.class)
@Table(name = "vehicle")
public class Vehicle {

	public enum FuelType {
		ΒΕΝΖΙΝΗ, ΠΕΤΡΕΛΑΙΟ, ΥΒΡΙΔΙΚΟ, ΗΛΕΚΤΡΙΣΜΟΣ, LPG, CNG
	}

	public enum UsageType {
		ΕΙΧ, ΦΙΧ, ΔΧ, ΤΑΞΙ, ΛΕΩΦΟΡΕΙΟ
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vin", nullable = false, length = 17)
	private String vin;

	@Column(name = "plate", nullable = false, length = 10)
	private String plate;

	// Derived from plate by the callback below, never set by callers. Kept in
	// Java rather than SQL because the Greek->Latin mapping is awkward there.
	@Column(name = "plate_normalized", nullable = false, length = 10)
	private String plateNormalized;

	@Column(name = "brand", nullable = false, length = 50)
	private String brand;

	@Column(name = "model", nullable = false, length = 100)
	private String model;

	@Column(name = "first_registration", nullable = false)
	private LocalDate firstRegistration;

	@Column(name = "license_issue_date")
	private LocalDate licenseIssueDate;

	@Column(name = "category", nullable = false, length = 10)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(name = "usage_type", nullable = false, length = 20)
	private UsageType usageType;

	@Column(name = "color", nullable = false, length = 50)
	private String color;

	@Column(name = "seats")
	private Short seats;

	// Nullable: an electric vehicle has none, and a 0 in the Excel is stored
	// as NULL so it cannot skew averages or sorting.
	@Column(name = "engine_cc")
	private Integer engineCc;

	@Column(name = "power_kw", nullable = false, precision = 6, scale = 2)
	private BigDecimal powerKw;

	@Enumerated(EnumType.STRING)
	@Column(name = "fuel_type", nullable = false, length = 20)
	private FuelType fuelType;

	@Column(name = "engine_number", length = 50)
	private String engineNumber;

	@Column(name = "co2")
	private Integer co2;

	@Column(name = "emission_standard", length = 20)
	private String emissionStandard;

	@Column(name = "weight_kg")
	private Integer weightKg;

	// Snapshot of the address on the licence document (C.1.3), deliberately
	// not synchronised with the owner's current address.
	@Column(name = "license_street", length = 200)
	private String licenseStreet;

	@Column(name = "license_city", length = 100)
	private String licenseCity;

	@Column(name = "license_postal_code", length = 5)
	private String licensePostalCode;

	// Generated column computed by PostgreSQL (V3 migration). Never written
	// from Java; Hibernate re-reads it after every insert and update.
	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "search_normalized", insertable = false, updatable = false)
	private String searchNormalized;

	// REMOVE, so that a hard delete removes the children through JPA and each
	// one is written to audit_log (DECISIONS §4). The database's ON DELETE
	// CASCADE alone would drop them without a trace. Delete a vehicle loaded
	// from the database: these lists are not kept in step on save, and a
	// delete from a stale copy fails rather than lose a child unlogged.
	@OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
	private List<Ownership> ownerships = new ArrayList<>();

	@OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
	private List<Policy> policies = new ArrayList<>();

	// Optimistic locking (SPEC §9): a save based on an outdated copy fails
	// instead of silently overwriting another user's change.
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	// On the entity so that no service can forget it (ARCHITECTURE §5).
	@PrePersist
	@PreUpdate
	private void fillPlateNormalized() {
		this.plateNormalized = TextNormalizationUtils.normalizePlate(plate);
	}

	public Long getId() {
		return id;
	}

	public String getVin() {
		return vin;
	}

	// In capitals on every write path, the Excel import included (Task 17).
	public void setVin(String vin) {
		this.vin = TextNormalizationUtils.storedVin(vin);
	}

	public String getPlate() {
		return plate;
	}

	// Stored without dashes or spaces, in capitals and without accents (Tasks
	// 14, 17). Done here rather than in the callback below so that every write
	// path, the Excel import included, stores it that way, and the audit log
	// sees the value that is stored.
	public void setPlate(String plate) {
		this.plate = TextNormalizationUtils.storedPlate(plate);
	}

	public String getPlateNormalized() {
		return plateNormalized;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public LocalDate getFirstRegistration() {
		return firstRegistration;
	}

	public void setFirstRegistration(LocalDate firstRegistration) {
		this.firstRegistration = firstRegistration;
	}

	public LocalDate getLicenseIssueDate() {
		return licenseIssueDate;
	}

	public void setLicenseIssueDate(LocalDate licenseIssueDate) {
		this.licenseIssueDate = licenseIssueDate;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public UsageType getUsageType() {
		return usageType;
	}

	public void setUsageType(UsageType usageType) {
		this.usageType = usageType;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public Short getSeats() {
		return seats;
	}

	public void setSeats(Short seats) {
		this.seats = seats;
	}

	public Integer getEngineCc() {
		return engineCc;
	}

	public void setEngineCc(Integer engineCc) {
		this.engineCc = engineCc;
	}

	public BigDecimal getPowerKw() {
		return powerKw;
	}

	public void setPowerKw(BigDecimal powerKw) {
		this.powerKw = powerKw;
	}

	public FuelType getFuelType() {
		return fuelType;
	}

	public void setFuelType(FuelType fuelType) {
		this.fuelType = fuelType;
	}

	public String getEngineNumber() {
		return engineNumber;
	}

	public void setEngineNumber(String engineNumber) {
		this.engineNumber = engineNumber;
	}

	public Integer getCo2() {
		return co2;
	}

	public void setCo2(Integer co2) {
		this.co2 = co2;
	}

	public String getEmissionStandard() {
		return emissionStandard;
	}

	public void setEmissionStandard(String emissionStandard) {
		this.emissionStandard = emissionStandard;
	}

	public Integer getWeightKg() {
		return weightKg;
	}

	public void setWeightKg(Integer weightKg) {
		this.weightKg = weightKg;
	}

	public String getLicenseStreet() {
		return licenseStreet;
	}

	public void setLicenseStreet(String licenseStreet) {
		this.licenseStreet = licenseStreet;
	}

	public String getLicenseCity() {
		return licenseCity;
	}

	public void setLicenseCity(String licenseCity) {
		this.licenseCity = licenseCity;
	}

	public String getLicensePostalCode() {
		return licensePostalCode;
	}

	public void setLicensePostalCode(String licensePostalCode) {
		this.licensePostalCode = licensePostalCode;
	}

	public String getSearchNormalized() {
		return searchNormalized;
	}

	public List<Ownership> getOwnerships() {
		return ownerships;
	}

	public List<Policy> getPolicies() {
		return policies;
	}

	public Long getVersion() {
		return version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
