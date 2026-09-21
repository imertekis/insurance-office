# Code Review: Tasks 11a to 15

## 1. Policy Overlap on Edit
* **Finding:** Two policies cannot end up overlapping through an edit.
* **Analysis:** In `PolicyService.update()`, the code calls `checkFields()`, passing the edited policy's ID as `policyId`. This correctly populates the `excludeId` parameter in `PolicyRepository.findOverlapping(vehicleId, policyId, startDate, endDate)`. The query's strict inequalities (`p.startDate < :endDate and p.endDate > :startDate`) flawlessly permit touching dates (e.g., renewing on the exact end date) while still properly rejecting genuine overlaps against *other* policies on the vehicle. 

## 2. Ownership Form Parameter Parsing & Error Handling
* **Finding:** The Greek comma and a lone "100,00" are parsed successfully, and bad input triggers a clear form error, not a 500 error page.
* **Analysis:** `OwnershipController.submit` purposefully binds the POST payload to a `MultiValueMap<String, String>` rather than a standard `List<String>`. This circumvents Spring Web's default behavior that would otherwise erroneously split strings like "100,00" at the comma. In `OwnershipService.share`, the comma is safely replaced with a dot before `BigDecimal` parsing. If a user submits unparseable text (e.g., letters), `share` safely adds a field violation, avoids a `NullPointerException` during the 100% check, and ultimately throws a `BusinessException`. The controller catches this and renders a clean UI error via `FormErrors.show()`.

## 3. Task 14 (Plate Normalization & V4 Migration)
* **Finding:** The Excel importer path can still store plates with dashes, though the V4 migration is safe from duplicates.
* **Analysis:** 
    * **Write Path Leak:** While `VehicleService.cleaned()` successfully strips separators via `TextNormalizationUtils` before persisting, `ExcelImporterService.readVehicleFields()` does not. It simply assigns `row.requiredText(PLATE)` to `vehicle.setPlate()`. Consequently, a re-import can reintroduce dashes into the `plate` column.
    * **V4 Duplicates:** The V4 migration (`V4__strip_plate_separators.sql`) modifies only the `plate` column. As the comments note, `plate_normalized` already had its dashes stripped natively in Java prior to the migration. Therefore, it is impossible for the migration to generate duplicate `plate_normalized` values, because the database's unique constraint on that column already prevented duplicate canonical plates from ever co-existing.

## 4. Task 15 (Collation & Unbounded Queries)
* **Finding:** The ICU collation resolves Greek sorting perfectly, but several pages still issue unbounded queries.
* **Analysis:** 
    * **Collation:** The `el-GR-x-icu` collation applied on `customer.name_sort` behaves correctly, placing accented capitals properly and ignoring cases until tie-breaks.
    * **Unbounded Queries:** While the main list pages (Task 15) are correctly paginated, earlier views remain unbounded and vulnerable to loading massive datasets into memory. `DashboardService.expiries` uses an unpaginated query (`findNotRenewedEndingBetween`), and `policyRepository.findInsuranceCompanies()` does the same. Furthermore, the Detail Cards execute unbounded collection fetches: `CustomerService.findDetail` unconditionally loads all historical ownerships and policies, and `VehicleService.findDetail` loads all policies. 

## 5. Task 13 (Owner-at-Start Rule)
* **Finding:** The boundary day math is solid, but `CustomerService.findDetail()` displays the wrong customer's policies.
* **Analysis:** 
    * `PolicyCustomers.primaryOwnerOn()` works flawlessly for the Vehicle card. A policy starting precisely on the transfer date is correctly attributed to the new owner because of the strict inequality (`day.isBefore(ownership.getToDate())`).
    * **The Flaw:** In `CustomerService.findDetail()`, the UI lists policies via `policyRepository.findByOwnerWithVehicleAndIntermediary(customerId)`. This query joins all policies for any vehicle the customer currently owns *or previously owned*. It fails to constrain the policies to those active *during* the customer's ownership period. As a result, if Customer A sells a car to Customer B, any future policies issued by Customer B will misleadingly show up on Customer A's detail card.
