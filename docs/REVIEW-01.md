# Code Review: Tasks 1 & 2

## 1. Correctness of the IMMUTABLE unaccent wrapper and the generated column
- **IMMUTABLE Wrapper:** The implementation in `V2__unaccent_wrapper.sql` is perfectly correct. Pinning the dictionary using `'public.unaccent'::regdictionary` and declaring the wrapper as `IMMUTABLE STRICT` is the canonical PostgreSQL workaround to safely use `unaccent` in a generated column or index.
- **Generated Column:** The column is implemented correctly. The concatenation logic utilizing `coalesce(field || ' ', '')` cleverly handles `NULL` values and spaces between words, while `rtrim()` efficiently trims the final trailing space. Furthermore, the explicit use of `COLLATE pg_unicode_fast` correctly guarantees deterministic upper-casing independent of OS-level locale settings.

## 2. Divergences between Java and SQL normalization (uncovered by tests)
While `SearchNormalizationConsistencyTest` excellently covers most of the behavior, there are two edge cases where the Java and SQL logic will diverge which are not covered by the tests:
- **Trailing Whitespace:** The SQL implementation uses `rtrim(...)` which inherently strips any trailing whitespace present at the end of the concatenated entity string (e.g., if a user's last name accidentally contains a trailing space, it will be removed). However, `TextNormalizationUtils.normalizeText()` does **not** trim whitespace. If a search query is submitted with trailing spaces (e.g. `"Αλεξίου "`), Java will yield `"ΑΛΕΞΙΟΥ "` and it will fail to match the stripped string in the database.
- **Non-decomposing Unicode Characters:** As accurately acknowledged in the Java comment, characters like `ø`, `æ`, `œ`, and `ł` are not decomposed by Unicode `NFD`, meaning Java's `TextNormalizationUtils` will output `Ø`, `Æ`, `Œ`, `Ł`. Conversely, the PostgreSQL `unaccent` extension expands them (`æ` -> `ae` -> `AE`). The `ValueSource` in the test suite currently omits these characters, masking this divergence.

## 3. SQL Injection Exposure
- **No SQL injection vulnerabilities exist in the code written so far.** 
- The codebase rightfully leverages Spring Data JPA (`CustomerRepository`), which inherently uses safe prepared statements.
- The `SearchNormalizationConsistencyTest` makes use of `JdbcTemplate` with proper parameter binding (`?`), avoiding any dynamic string concatenation. 
- The Flyway SQL migrations (`V1` and `V2`) contain purely static DDL.

## 4. Layering Rules Compliance
- The structural layering rules outlined in `CLAUDE.md` are strictly followed.
- **Dependencies & Mapping:** `CustomerController` correctly delegates to `CustomerService` and returns a DTO (`CustomerDto`), ensuring entities never leak to the API layer. No controller touches the repository. 
- **Transactions:** The `@Transactional` boundary is appropriately placed on `CustomerService`.
- **Domain Logic:** `Customer` remains a pure persistence entity mapped with JPA annotations (including `@Generated` for the read-only computed column), maintaining its separation from business logic.
- **Utility Segregation:** `TextNormalizationUtils` correctly operates statelessly without Spring context dependencies in the `util` package, establishing a single source of truth for Java-side normalization.
