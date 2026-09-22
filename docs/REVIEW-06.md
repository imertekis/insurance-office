# Code Review: Ultrareview of 30681e1..main

> Note: this run's task notification reported 6 confirmed findings, all
> **nit** severity, not 11. Recorded below as received; nothing was
> invented to pad the count.

## 1. Missing handler for unique-constraint race on create/update
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/controller/ViewExceptionHandler.java`
* **What goes wrong:** Customer/Vehicle/Policy create/update flows check uniqueness (ΑΦΜ, VIN, plate, policy number) with a SELECT before the INSERT/UPDATE, relying on DB unique indexes as the real guard. `ViewExceptionHandler` only maps `NotFoundException`, so two clerks racing to save the same value get an unhandled `DataIntegrityViolationException` (500) instead of a form error on the second commit.
* **One-line fix:** Add a `@ExceptionHandler(DataIntegrityViolationException.class)` in `ViewExceptionHandler` that re-renders the form with a "already exists" field error.

## 2. Ownership form drops a row for a deleted customer on re-render
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/service/OwnershipService.java` (`ownersForm`, ~line 122)
* **What goes wrong:** If a customer added to the in-progress owners form is deleted before save, `ownersForm` silently skips that row on re-render, but `saveOwners`'s violation keys (`percentages[i]`) are built from the original, un-shifted submission list — so the error banner points at the wrong row.
* **One-line fix:** Keep a placeholder row (or re-key violations) instead of skipping missing customers when rebuilding the form.

## 3. Transfer-date conflict message uses ISO date, not Greek format
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/service/OwnershipService.java` (`saveOwners`, ~line 204-206)
* **What goes wrong:** The message concatenates `ownership.getFromDate()` directly (`LocalDate.toString()` → `yyyy-MM-dd`) instead of using the file's own `GREEK_DATE` formatter used everywhere else, producing an inconsistent date format for Greek-speaking clerks.
* **One-line fix:** Format the date with `GREEK_DATE.format(...)` before concatenating.

## 4. `PolicyController.renew()` loads the same policy twice
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/controller/PolicyController.java` (~line 61-63)
* **What goes wrong:** `renew()` calls `policyService.renewal(id)` and then `policyService.find(id).policyNumber()`, both resolving to the same `findById` lookup — an avoidable extra DB round trip.
* **One-line fix:** Have `renewal(id)` also return/expose the source policy number, or reuse its loaded entity.

## 5. Customer display-name formatting duplicated 5x
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/service/CustomerService.java` (~line 274-276, plus `OwnershipService`, `PolicyService`, `VehicleService`, `PolicyCustomers`)
* **What goes wrong:** The "lastName, or lastName + firstName" display logic is copy-pasted verbatim in 5 places; a future change to the rule (e.g. legal entities) risks drifting between copies.
* **One-line fix:** Move it to one shared helper (e.g. a method on `Customer`) and call it from all 5 sites.

## 6. `GREEK_DATE` formatter constant redeclared in 4 services
* **Severity:** nit
* **File:** `src/main/java/gr/insuranceoffice/service/CustomerService.java` (line 42, plus `OwnershipService`, `PolicyService`, `VehicleService`)
* **What goes wrong:** Each of the 4 services declares its own identical `private static final DateTimeFormatter GREEK_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")`, risking drift if the format ever changes.
* **One-line fix:** Move the constant to one shared location (e.g. a small date-formatting utility) and reference it from all 4.
