# Code Review: Tasks 3 & 4

## 1. Excel Importer: Transactions, Error Handling, and Partial Failure
- **Transaction Boundaries:** `ExcelImporterService.importFiles()` is correctly annotated with `@Transactional`. The import enforces an "all or nothing" policy.
- **Partial Failure:** If a file parsing error or logical validation error occurs, it is collected into a list of errors, and the transaction rolls back at the end by throwing `ExcelImportException`. 
- **Database Constraints:** For constraints enforced by the database, `customerRepository.flush()` is called per row to catch `DataIntegrityViolationException`. While this successfully identifies the exact row that failed, PostgreSQL aborts the transaction on the first error. The importer rightfully acknowledges this by throwing `ExcelImportException` immediately, meaning it stops processing and cannot collect further DB-level errors.

## 2. Entity Relationships and Cascade Rules
- **SQL Cascade:** The database schema correctly implements `ON DELETE CASCADE` on `ownership.vehicle_id`, `ownership.customer_id`, and `policy.vehicle_id`.
- **JPA Cascade Mismatch:** The JPA entities (`Customer` and `Vehicle`) declare `@OneToMany` relationships but lack `cascade = CascadeType.REMOVE` (or `ALL`) and `orphanRemoval = true`. While the DB will handle hard deletes, JPA is unaware of this cascade. This can leave the persistence context stale or cause `ConstraintViolationException`s if JPA attempts to manage the deletion order without knowing about the cascade.

## 3. Bypassed Validation
- **Direct Repository Usage:** `ExcelImporterService` injects Spring Data repositories directly, entirely bypassing the service layer where business rules are expected to live.
- **Ownership Shares:** The `Ownership` entity's documentation explicitly states: *"Shares per vehicle sum to 100 and exactly one row is primary; both are checked in the service layer."* Because the importer bypasses the service layer and the database schema (`V3__full_schema.sql`) lacks constraints for multi-row validations (sum = 100, exactly one primary), the importer can silently persist invalid ownership configurations.
- **Mobile Number Validation:** The business rule stating that the mobile number is required for the primary owner of a vehicle with a current policy is completely bypassed during the import.

## 4. Performance: N+1 Patterns in the Import Loop
- **N+1 Reads:** Inside `importArchiveRow`, the importer queries the database individually for every single row. It calls `findByVin`, `findByPolicyNumber`, `findByFullName`, and `findByVehicle` inside the loop. This results in thousands of individual `SELECT` queries for a standard archive file.
- **JDBC Batching Bypassed:** The code calls `customerRepository.flush()` explicitly inside the `for` loop for every single row to catch exceptions early. This completely defeats Hibernate's JDBC statement batching, forcing one or more `INSERT`/`UPDATE` statements to be executed and round-tripped to the database for every row.
