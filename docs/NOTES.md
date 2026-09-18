# Open items to verify during implementation

- VIN regex must be ^[A-HJ-NPR-Z0-9]{17}$ (exclude I, O, Q),
  not ^.{17}$ — otherwise any 17-char string matches as VIN
- Plate/text normalization must live in ONE shared utility used
  by both the write path (@PrePersist) and the read path
  (SearchService) — no duplicated algorithm

## Import risks

- Re-import overwrites app edits, blanks included: a "-" in the
  Excel clears a stored value. Safe for the one-off migration,
  dangerous once the office edits data in the app.
- Re-import removes ownerships not present in the file. An
  incomplete Excel can silently break ownership links.
- Before the app goes live, decide whether import should become
  insert-only, or require an explicit "overwrite existing"
  confirmation. For now the `import` profile (`ExcelImportRunner`)
  refuses a database that already has customers or vehicles unless
  `--import.allow-existing-data=true` is given. That flag is the only
  confirmation; nothing checks what would be overwritten.
- The ownership check on import counts only the owners written in
  the file. Ownerships with transfer dates are left untouched and
  not counted, so once the app records transfers, a re-import
  could leave a vehicle with shares above 100%.

## Deferred review findings

Not fixed yet. "Finding 2" and "Finding 4" are from `docs/REVIEW-02.md`,
entries marked REVIEW-03 from `docs/REVIEW-03.md`; the rest came up while
implementing the task named.

- **Finding 2, JPA cascade/orphan mismatch.** Partly fixed in Task 6: the
  `@OneToMany` collections on `Customer` and `Vehicle` now have
  `cascade = REMOVE`, so a hard delete removes ownerships and policies
  through JPA and logs each one. Still open:
  - No `orphanRemoval`. Decide in Task 11, when the ownership form edits
    all owners of a vehicle together.
  - The collections are not kept in step on save (`Ownership.setVehicle`
    does not add to `Vehicle.ownerships`). A delete must start from an
    entity loaded from the database; deleting the copy `save()` returned
    fails with `TransientPropertyValueException` instead of losing
    children. Services should load by id before deleting.
- **Finding 4, `flush()` inside the import loop.** `ExcelImporterService`
  flushes after every row to pin a database error to its row. This defeats
  Hibernate's JDBC batching, so every row makes its own round trips. The
  per-row `findBy…` lookups add N+1 reads on top of that.
- **Policy number search (Task 5).** A policy number is only recognised
  as 10 digits starting with `21`, which fits the sample data but not
  other insurers' formats. `policy_number` is not part of any
  `search_normalized`, so other formats cannot be found at all. Consider
  adding it to a searchable field or adding a dedicated lookup.
- **Unknown ΑΦΜ finds nothing (Task 5).** A 9-digit input that is not a
  known ΑΦΜ returns no results instead of falling back to free text.
  Decide in Task 9 whether the UI should explain this or the search
  should fall back.
- **Mobile rule from the other side (Task 11).** Task 7 checks the rule
  when a customer is saved. Making a customer without a mobile the primary
  owner (ownership form) or adding a current policy to their vehicle
  (policy form) must be refused too, in `OwnershipService` and
  `PolicyService`. `OwnershipRepository.isCurrentPrimaryOwnerOfInsuredVehicle`
  holds the definition: `is_primary`, `to_date IS NULL`, and a policy with
  `start_date <= today <= end_date`.
- **REVIEW-03 finding 1, extra SELECT per update.**
  `PostgreSQLVersionCheckingDialect` turns off `UPDATE ... RETURNING` so
  that a version conflict on Customer or Vehicle is reported as one
  (Task 7). The cost: every update of those two entities is an `UPDATE`
  plus a `SELECT` for `search_normalized`. Negligible for a 3-user office,
  but it adds to the import's round trips (Finding 4). Revisit when
  Hibernate checks the row count before reading `RETURNING` values;
  `OptimisticLockingTest` shows whether the dialect can go.
- **REVIEW-03 finding 2, audit log gaps.**
  - `plate_normalized` is not in a Vehicle `UPDATE` diff, because entity
    listeners run before the entity's own `@PreUpdate`. Accepted in
    Task 6: `plate` is in the diff, and `DELETE` rows hold the right value.
  - Bulk JPQL or native `UPDATE`/`DELETE` statements bypass JPA callbacks
    and are never logged. None exist today. Do not add them for audited
    entities, or write the `audit_log` rows alongside them.
  - Each flush writes its own `UPDATE` row, so an entity changed and
    flushed several times in one transaction gets several rows instead of
    one. The importer flushes after every Excel row, so a customer who
    appears on several rows can be logged once per row.
- **REVIEW-03 finding 4, importer bypasses customer validation.**
  `ExcelImporterService` saves customers through `CustomerRepository`, so
  the Task 7 rules in `CustomerService` (ΑΦΜ check digit, phone and ΤΚ
  formats, mobile for the primary owner of an insured vehicle) are never
  checked on import. Harmless for the one-off migration, but **before the
  office ever re-imports**, customer writes must go through
  `CustomerService`, with rows it refuses listed in the import report
  rather than dropped silently. This pairs with the "Import risks" above:
  a re-import that overwrites app edits would also write values the app
  itself refuses.

- **No user-management screen (Task 10).** Accounts are made and
  passwords reset only with the `create-user` profile. Nothing lets the
  ΔΙΑΧΕΙΡΙΣΤΗΣ add a clerk, deactivate one or change their own password
  from the application, and SPEC §2 expects that. Worth a task of its own.
- **Roles are not enforced yet (Task 10).** Every logged-in user may do
  everything: the role only becomes an authority (`ROLE_ΥΠΑΛΛΗΛΟΣ`,
  `ROLE_ΔΙΑΧΕΙΡΙΣΤΗΣ`). SPEC §2 reserves deletions, user management,
  the audit log and exports for the ΔΙΑΧΕΙΡΙΣΤΗΣ; wire that up when
  Task 11 adds the actions those rules are about.
