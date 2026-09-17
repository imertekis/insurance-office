# Code Review: Tasks 5, 6 & 7

## 1. PostgreSQLVersionCheckingDialect: RETURNING and Side Effects
- **Is disabling `RETURNING` for updates safe?** Yes, it is safe. Hibernate handles the lack of `UPDATE ... RETURNING` support by gracefully falling back to a two-step process: it first executes the `UPDATE` statement, checks the affected row count (to verify optimistic locking via `@Version`), and then executes a separate `SELECT` query to fetch the database-generated columns (such as `search_normalized`).
- **Side effects:** Disabling `RETURNING` introduces a performance penalty. It forces an extra query for updates, as every update to an entity with `@Generated` properties (like `Customer` or `Vehicle`) now requires two database round-trips (`UPDATE` followed by `SELECT`) instead of a single efficient `UPDATE ... RETURNING` statement.

## 2. Audit Listener: Missing or Duplicating Entries
- **Missed Entries:**
  - **Derived Columns:** Because `@EntityListeners` (like `AuditListener`) execute *before* the entity's own internal lifecycle callbacks (like `@PreUpdate`), the audit listener captures the entity state before those callbacks run. As a result, the `UPDATE` audit log misses changes to derived columns like `Vehicle.plate_normalized`, which is updated in `Vehicle.fillPlateNormalized()` after the listener has already taken its snapshot.
  - **Bulk Operations:** Any bulk `UPDATE` or `DELETE` executed via JPQL or native queries completely bypasses JPA lifecycle callbacks, meaning the `AuditListener` would miss them entirely (though currently, no such bulk operations exist in the codebase).
- **Multiple (Duplicated) Entries:** If an entity is modified and `flush()` is called multiple times within the same transaction (as seen in the batch loops of `ExcelImporterService`), Hibernate triggers `@PreUpdate` on every flush. Since the listener writes to the database directly via JDBC, this results in multiple `UPDATE` audit log entries for a single entity in a single transaction, rather than one consolidated entry.

## 3. Search Specifications: Injection Exposure and Query Plan Degradation
- **SQL Injection Exposure:** The implementation is safe from SQL injection. The Criteria API (`cb.like()`) safely uses parameter binding (or properly escaped string literals) for its arguments. Furthermore, the `LIKE_WILDCARDS` regex safely escapes literal `%`, `_`, and `\` characters from the user input.
- **Query Plan Degradation:** Yes, the query plan will severely degrade with many terms. The specification splits the input using `WHITESPACE.split(input)` without enforcing any limit on the number of generated terms. If a user inputs a string with hundreds of spaces, it generates hundreds of `AND LIKE` conditions (and `OR` conditions for vehicle plates). This forces PostgreSQL to evaluate a massive AST with hundreds of trigram index scans and bitmap AND operations, which can lead to exponential planning times, high CPU consumption, and potential Denial of Service (DoS).

## 4. Validation Bypass
- **Direct Repository Usage:** Yes, validations can be bypassed by any path that injects and calls `CustomerRepository` directly instead of routing through `CustomerService`. 
- **Excel Importer Bypass:** Specifically, `ExcelImporterService` directly invokes `customerRepository.save(...)`. This circumvents `CustomerService.checkFields()`, allowing invalid data (such as malformed phone numbers, invalid `TAX_ID` check digits, and missing mandatory mobile numbers for primary owners of insured vehicles) to be persisted without domain validation.
