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
- The importer and the ownership form (Task 11c) treat a departing
  owner differently: the importer deletes the row, the form closes it
  with `to_date`. And an owner the form closed who is still in the
  Excel makes the re-import fail: the importer adds them back as a new
  row with no `from_date`, which collides with the closed row on the
  unique index `(vehicle_id, customer_id, from_date) NULLS NOT DISTINCT`.
  One more reason the import must become insert-only, or learn about
  transfers, before the office re-imports.
  - **Known limit of re-import, reproduced while specifying Task 18.**
    The row the form closed has no `from_date` and a `to_date`; the
    importer treats only rows with neither as current, so it inserts the
    owner again and the insert collides. The whole import stops and
    keeps nothing.
  - Task 18 only turns the PostgreSQL text into a Greek row message
    naming both ΑΦΜ columns. Task 19 does not change it either: it
    reopens rows from the ownership form, not from the import. The limit
    stays until the import learns about transfers.

## Open questions

Every decision still to be made in the project. CLAUDE.md points here; an
entry leaves this list when the decision is recorded (in DECISIONS, CLAUDE.md
or the task). Details that live elsewhere in this file are linked, not
repeated.

- **How should a re-import treat existing data?** Insert-only, an explicit
  "overwrite existing" confirmation, or an import that knows about
  transfers. Today the only guard is `--import.allow-existing-data=true`.
  Decide before the app goes live, and in any case before the office
  re-imports. Details: «Import risks» above, and REVIEW-03 finding 4
  (customer rules not checked on import) under «Deferred review findings».
- **How long should a login last?** The session timeout is the Spring
  Boot default (30 minutes idle), with no "remember me". Decide before the
  office starts using the app. Details: «REVIEW-04, session timeout».
- **What should the search do with a 9-digit input that is no known
  ΑΦΜ?** Today it looks up the ΑΦΜ only and shows «Κανένα αποτέλεσμα.».
  The entry said to decide in Task 9; Task 9 kept the empty result without
  recording a decision. Options: explain it on the page, fall back to free
  text, or keep it. Details: «Unknown ΑΦΜ finds nothing».
- **How should other insurers' policy numbers be found?** Only 10-digit
  numbers starting with `21` are recognised, and `policy_number` is in no
  `search_normalized`. Needs the other insurers' formats. Details: «Policy
  number search».
- **Which of the ΔΙΑΧΕΙΡΙΣΤΗΣ functions of SPEC §2 are needed before
  go-live?** User management, the audit-log view and exports do not exist,
  and neither does a screen for intermediaries (TASKS Task 11e leaves them
  here). Details: «No user-management screen» and «Roles enforced for
  deletions only».
- **Which alphabet should a plate's look-alike letters be stored in?**
  Task 17 uppercases plates and strips accents on save but keeps the
  alphabet as typed (DECISIONS §5), so today it depends on the source:
  - the import stores what the Excel has (all 8 plates of the sample
    are Latin-only);
  - the form stores what the keyboard typed (usually Greek).

  On screen «ΝΚΝ7777» and «NKN7777» look identical, and search,
  duplicate checks and sorting treat them as one plate
  (`plate_normalized`). The difference only shows when a plate is
  copied into another system (an insurer's portal) or exported.

  Converting look-alikes to Greek is out of scope. It would have to
  recognise the standard Greek format (three of the 14 look-alike
  letters and four digits) and leave everything else alone: foreign
  and diplomatic plates are legitimately Latin, and special plates keep
  Greek-only letters (CLAUDE.md, resolved conflict 5). Decide before
  exports exist, or as soon as a portal is found to reject one alphabet.

## Deferred review findings

Not fixed yet. "Finding 2" and "Finding 4" are from `docs/REVIEW-02.md`,
entries marked REVIEW-03 or REVIEW-04 from `docs/REVIEW-03.md` and
`docs/REVIEW-04.md`; the rest came up while implementing the task named.

- **Finding 2, JPA cascade/orphan mismatch.** Partly fixed in Task 6: the
  `@OneToMany` collections on `Customer` and `Vehicle` now have
  `cascade = REMOVE`, so a hard delete removes ownerships and policies
  through JPA and logs each one. Still open:
  - No `orphanRemoval`, and none is needed: Task 11c closes a removed
    owner with `to_date` instead of deleting the row, so an ownership
    never becomes an orphan. Revisit only if something starts removing
    ownerships from a vehicle's collection.
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
  Task 9 kept this without deciding; the question is under «Open
  questions».
- **Mobile rule from the other side (Tasks 11c, 11d): done.** Task 7
  checks the rule when a customer is saved; Task 11c refuses making a
  customer without a mobile the primary owner of an insured vehicle, and
  Task 11d refuses a policy that would be in force today for a vehicle
  whose primary owner has none. Kept here because TASKS links to it.
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

- **REVIEW-04, unbounded queries behind the pages.** Nothing limits the
  rows the dashboard and the cards load: `findNotRenewedEndingBetween`,
  `findInsuranceCompanies`, and the ownership and policy lists of
  `CustomerService.findDetail` and `VehicleService.findDetail`. Invisible
  with the 8 vehicles of the sample, but a fleet customer or a full
  archive would pull everything into memory. The search caps each group
  at 50 hits, and the three list pages of Task 15 are paged (50 rows).
  The dashboard and the cards are still to do.
- **REVIEW-04, session timeout left at the default.** Nothing sets
  `server.servlet.session.timeout`, so the Spring Boot default applies
  (30 minutes idle) and there is no "remember me". SPEC §2 implies a
  clerk should not have to log in again and again during a working day.
  Decide on a longer timeout before the office starts using the app.
- **No user-management screen (Task 10), and no intermediary screen.**
  Accounts are made and passwords reset only with the `create-user`
  profile. Nothing lets the ΔΙΑΧΕΙΡΙΣΤΗΣ add a clerk, deactivate one or
  change their own password from the application, and SPEC §2 expects
  that. Intermediaries are in the same position: the policy form of Task
  11d only picks from existing ones, and today they are created only by
  the import. Worth a task of its own, covering both.
- **Roles enforced for deletions only (Tasks 10, 11e).** Since Task 11e
  only the ΔΙΑΧΕΙΡΙΣΤΗΣ may delete, checked with `@PreAuthorize` on the
  services. SPEC §2 also reserves user management, the audit log and
  exports for the ΔΙΑΧΕΙΡΙΣΤΗΣ; none of them exists yet, and each will
  need the same guard when it does. Recovering a deleted record from
  `audit_log` (DECISIONS §4) is still done by hand in SQL.
- **Task 15, the sort key depends on ICU.** `customer.name_sort` and its
  index use the `el-GR-x-icu` collation. V5 fails at once on a PostgreSQL
  built without ICU, as the official image and the EDB installers are not.
  Should a PostgreSQL upgrade bring a new ICU version, PostgreSQL warns
  about the collation version, and the index has to be rebuilt with
  `REINDEX INDEX idx_customer_name_sort` before the order can be trusted.
- **REVIEW-05 finding 3, checked and not a bug.** The review said the
  Excel importer can store plates with dashes, because
  `ExcelImporterService` does not strip them and only
  `VehicleService.cleaned()` does. But the importer sets the plate with
  `Vehicle.setPlate`, and the stripping is in that setter (Task 14), so
  every path that writes a plate goes through it, the import included.
  `ExcelImporterServiceTest.storesAPlateWithoutItsDashOrSpaceAndKeepsItSoOnARerun`
  imports a plate with a hyphen, a space and an en dash and asserts all
  three are stored without one, and that a re-import leaves them so and
  logs no vehicle change; it fails if the setter stops stripping.
- **REVIEW-05 finding 5, a late co-owner does not see earlier policies
  (accepted trade-off).** The customer card lists a policy only if it
  started within one of the customer's ownerships of the vehicle, by the
  rule of Task 13. A co-owner who joined after a policy started
  therefore does not see that policy on their card, though it insures a
  vehicle they own. The alternative, listing any policy that overlaps the
  ownership period, would show the seller's still-running policy on the
  buyer's card when a vehicle is sold in the middle of one. The gap
  closes by itself at the next renewal, which starts inside the
  co-owner's period. The vehicle card still lists every policy.
