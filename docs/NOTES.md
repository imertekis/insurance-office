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
  confirmation.
- The ownership check on import counts only the owners written in
  the file. Ownerships with transfer dates are left untouched and
  not counted, so once the app records transfers, a re-import
  could leave a vehicle with shares above 100%.

## Deferred review findings

Not fixed yet. Findings 2 and 4 are from `docs/REVIEW-02.md`; the rest
came up while implementing the task named.

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

## Doc conflicts to settle

- **Audit user: username or id (Task 10).** TASKS Task 10 says the
  `SecurityContextHolder` feeds the **username** to the `AuditListener`,
  but ARCHITECTURE §6 says the listener takes the **user ID**, and
  `audit_log.user_id` is a `BIGINT` referencing `app_user`. Until then the
  listener writes `user_id` as `NULL`. Settle before implementing Task 10.
