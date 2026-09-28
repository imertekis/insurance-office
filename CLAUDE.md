# CLAUDE.md

Local multi-user web app for an insurance office (customers, vehicles,
ownership, policies, intermediaries). Replaces Excel files.

Source of truth, read before any task:
- `docs/SPEC.md` — requirements, validation rules, acceptance criteria
- `docs/DATA_MODEL.md` — tables, columns, enum values, indexes
- `docs/ARCHITECTURE.md` — stack, package layout, mechanisms
- `docs/DECISIONS.md` — accepted/rejected deviations from the spec
- `docs/NOTES.md` — open items that override older wording elsewhere
- `docs/TASKS.md` — the implementation plan

## Workflow — ONE task at a time

1. Work on exactly ONE task from `docs/TASKS.md`, in order, unless the
   user names a different one.
2. Do only what that task lists. Do not pull in work from later tasks
   (e.g. no Thymeleaf before Task 8, no Spring Security before Task 10).
3. A task is **done only when `./mvnw verify` passes** (all unit and
   Testcontainers integration tests green) and the task's acceptance
   criteria are met. Never report a task done with failing, skipped or
   `@Disabled` tests. If a test cannot pass, stop and report why.
   From Task 24 on, a task that touches `static/js/app.js`, or the markup
   it relies on in the templates (ids, `data-*` attributes), is done only
   when `./mvnw verify -Pbrowser` passes too.
4. When the task is done: summarize what changed, show the test result,
   and **STOP**. Do not start the next task without being asked.
5. If the docs conflict or are ambiguous for the current task, stop and
   ask. Do not silently pick one side. Known conflicts are listed below.

## Stack

- **Java 21** (LTS). Set `<java.version>21</java.version>` in `pom.xml`.
- Spring Boot, Maven with the Maven wrapper (`./mvnw`, committed).
- PostgreSQL (with `unaccent`, `pg_trgm`), run locally via `docker compose`.
- Flyway, plain SQL migrations in `src/main/resources/db/migration`,
  named `V<n>__<description>.sql`. Never edit a migration once committed;
  add a new one.
- Spring Data JPA / Hibernate. MapStruct for Entity↔DTO mapping.
- Thymeleaf (+ a little vanilla JS/HTMX) for UI. No SPA, no Node.js build.
  The rule covers the application and its build: nothing the app ships,
  and nothing `./mvnw verify` needs, may require Node or npm. A test tool
  in a separate Maven profile may carry its own runtime, as Playwright's
  embedded Node does in the `browser` profile (Task 24).
- Tests: JUnit 5, AssertJ, Testcontainers (real PostgreSQL, never H2).
- Apache POI for the Excel import.

## Packages and layering

Root package: `gr.insuranceoffice`. Technical layering, strictly:

| Package | Contains |
|---|---|
| `controller` | Spring MVC controllers. Call services only. |
| `service` | Business logic, validation, `@Transactional` boundaries. |
| `repository` | Spring Data JPA interfaces only. |
| `entity` | JPA entities only. |
| `dto` | DTOs to/from the UI/API. |
| `mapper` | MapStruct mappers (Entity↔DTO). |
| `config` | Spring configuration (Web, JPA, Security wiring). |
| `security` | Authentication/authorization logic. |
| `importer` | Excel import (POI) and its transformations. |
| `util` | Stateless helpers shared across layers (e.g. `TextNormalizationUtils`). No Spring beans, no dependencies on other app packages. |

Rules:
- Dependencies point downward: `controller → service → repository → entity`.
- Controllers never touch repositories and never receive or return entities.
  They use DTOs, and mapping is done by `mapper`.
- `@Transactional` goes on services, not on controllers or repositories.
- Business rules live in services. Entities hold only persistence concerns
  (JPA callbacks for normalization, `@Version`, listeners).
- `importer` writes through services/repositories. It must not duplicate
  validation or normalization logic.
- On the Java side, text/plate normalization lives in ONE utility
  (`TextNormalizationUtils`), used by `Vehicle`'s JPA callbacks and by
  `SearchService`. Never duplicate it in Java. `search_normalized` is the
  one exception and is computed in SQL (see "Resolved doc conflicts" §1).

## Naming and language

- DB table/column names and Java class/field names: **English**
  (`customer.tax_id`, `Vehicle.fuelType`).
- **Domain enum values stay in Greek**, exactly as written in
  `DATA_MODEL.md`. Do not transliterate or translate them:
  - `fuel_type`: `ΒΕΝΖΙΝΗ`, `ΠΕΤΡΕΛΑΙΟ`, `ΥΒΡΙΔΙΚΟ`, `ΗΛΕΚΤΡΙΣΜΟΣ`, `LPG`, `CNG`
  - `usage_type`: `ΕΙΧ`, `ΦΙΧ`, `ΔΧ`, `ΤΑΞΙ`, `ΛΕΩΦΟΡΕΙΟ`
  - `surcharge_type`: `ΝΕΟΣ_ΟΔΗΓΟΣ`, `ΗΛΙΚΙΑΣ`, `ΑΛΛΟ`
  - `role`: `ΥΠΑΛΛΗΛΟΣ`, `ΔΙΑΧΕΙΡΙΣΤΗΣ`
  
  Java enum constants use the same Greek identifiers, persisted with
  `@Enumerated(EnumType.STRING)`. Enums that `DATA_MODEL.md` defines in
  English stay English (`entity_type`: `INDIVIDUAL`/`COMPANY`; audit
  `action`: `CREATE`/`UPDATE`/`DELETE`/`VIEW`).
- UI labels are Greek. Source files are UTF-8.

## Domain rules to keep in mind

- `customer.tax_id` and `customer.mobile` are **nullable**. Missing ΑΦΜ
  gives a warning and never blocks saving. Mobile is required only for the
  primary owner of a vehicle with a current policy (DECISIONS §1, §2).
- `vehicle.engine_cc` is nullable. `0` on an electric vehicle is stored as `NULL`.
- **Hard delete only**. There is no `deleted_at` and no soft-delete filters.
  Recovery goes through `audit_log` (DECISIONS §4).
- **Only the ΔΙΑΧΕΙΡΙΣΤΗΣ deletes** (SPEC §2, Task 11e). Every delete is a
  service method with `@PreAuthorize(Roles.ADMINISTRATOR_ONLY)`, reached
  through a confirmation page; hiding the button is only a courtesy. A new
  delete must carry the same guard. Deletes load the entity first, so JPA
  cascades the children and `AuditListener` logs each one.
- Audit logging uses a custom JPA `@EntityListener` writing JSONB to
  `audit_log` in the same transaction. No Hibernate Envers.
- Optimistic locking uses `@Version` on customer, vehicle and policy.
  A conflict returns HTTP 409.
- Search is a single box with regex type detection (DECISIONS §3, REJECTED
  proposal: do not add a type dropdown). VIN regex is
  `^[A-HJ-NPR-Z0-9]{17}$`, never `^.{17}$` (NOTES). If a pattern is
  ambiguous, search all matching types and group the results (e.g. `21…`,
  see "Resolved doc conflicts" §3).
- Policy duration is never assumed (6- and 12-month policies both exist).
- Money is `NUMERIC(10,2)` / `BigDecimal`, never `double` or text.
- Ownership percentages per vehicle sum to 100. Exactly one `is_primary`
  per vehicle. Ownership is a join **entity** (two `@ManyToOne`), not
  `@ManyToMany`.
- Never commit real customer data. `*.xlsx`, `*.xls`, `*.csv` and `.env`
  are gitignored. Tests use synthetic fixtures only.

## Resolved doc conflicts

These override any older wording in `docs/`.

1. **`search_normalized` is computed in the DATABASE.** It is a
   `GENERATED ALWAYS AS (...) STORED` column built from an IMMUTABLE
   wrapper around `unaccent` (created by a Flyway migration).
   - Why: any write that bypasses JPA (Excel import, manual SQL,
     migrations) would otherwise leave it empty.
   - JPA maps it read-only (`insertable = false, updatable = false`) and
     never sets it from Java.
   - `plate_normalized` stays in Java, filled by `@PrePersist`/`@PreUpdate`
     on `Vehicle` using `TextNormalizationUtils`, because the Greek→Latin
     mapping is awkward in SQL. `vehicle.search_normalized` reads from
     `plate_normalized`.
   - This means accent removal plus upper-casing exists twice: in SQL for
     the write path and in `TextNormalizationUtils` for the search input.
     An integration test must prove that both give the same output for
     accented, mixed-case and final-sigma Greek input.
2. **The REST endpoints of Tasks 1 and 7 are gone.** They only proved the
   infrastructure, and Thymeleaf replaced them: `GET /api/customers` in
   Task 8, `/api/search` in Task 9, and `POST`/`PUT /api/customers` in
   Task 11a, whose form is now the only way to save a customer. The
   application serves HTML, with one exception since Task 21a:
   `GET /search/suggestions` answers JSON, for the header's own script
   (Task 21b). It is not an API: it needs a login like every page (without
   one it redirects to the login page, not JSON), it is sent `no-store`,
   and its texts are written by `SearchService`, so the script holds no
   wording.
3. **10 digits starting with `21` searches BOTH landline and policy
   number.** The clerk should not have to know the difference: run two
   queries and group the results by type.
4. **SPEC §7.1 wins for the expiry dashboard.** Filters are 7 / 30 / 60 / 90
   days, per insurer, and already expired. The default view is 30 days.
5. **Plate normalization maps only the 14 Greek/Latin look-alikes**
   (Α Β Ε Ζ Η Ι Κ Μ Ν Ο Ρ Τ Υ Χ). Greek-only letters (Γ, Δ, Σ…) stay Greek, so
   `ΑΒΓ-1234` ≠ `ABG-1234`. Standard plates only use the look-alikes; special
   plates (e.g. army `ΕΣ`) keep their Greek letters. The SPEC and DATA_MODEL
   examples use `ΑΒΕ-1234` = `ABE-1234`.
6. **The audit log records the user by id.** TASKS Task 10 said
   "username", ARCHITECTURE §6 said user ID, and `audit_log.user_id` is a
   `BIGINT` FK to `app_user`: the id wins, and the TASKS wording was fixed.
   The logged-in principal (`AppUserDetails`) carries the id, and
   `CurrentUser.id()` hands it to `AuditListener`. A change with nobody
   logged in, such as the Excel import, leaves `user_id` NULL.
7. **Policies may touch but not overlap.** Two policies of one vehicle
   overlap only if `start < other end AND other start < end`, so a renewal
   may start on the day the previous policy ends, as the office's data
   does (Task 11d). Task 12 prefills the new start with the current end.
8. **The expiry dashboard lists renewals still to do.** A policy counts
   as renewed when its vehicle has a policy with a later `start_date`, and
   renewed policies are left out of every view. "Already expired" means
   not renewed, with `end_date` in the last 90 days (yesterday included).
   A policy ending today is still in force, so it is expiring, not expired.
9. **Search suggestions take two lines on a phone.** TASKS Task 21b asks
   for one line per suggestion in a list as wide as the search field, and
   for a list that reads at 375px. There the field is some 233px wide, and
   one line showed a cut name and «ΑΦΜ 1…». Below 576px each suggestion is
   two lines, the name and then its second part, each cut with «…»; from
   576px up, one line. The list stays as wide as the field.
10. **Θέσεις stays a text field, with its range as a hint.** TASKS Task 23b
    asked for `min="1"` and `max="99"` on the field; Task 16d-1 made every
    number field `type="text"` with `inputmode`, where min/max do nothing,
    so that the browser never blocks what is typed and the server answers
    beside the field. Decided with the office: 16d-1 wins, and the field has
    the hint «Από 1 έως 99.» under it; 0 or 100 get the server's message
    (Task 23a).

## Open questions (ask before implementing)

Listed in the «Open questions» section of `docs/NOTES.md`, the one place
that tracks them. Ask before implementing anything that depends on one.

## Commands

```bash
docker compose up -d        # start PostgreSQL
./mvnw verify               # build + all tests (definition of done)
./mvnw verify -Pbrowser     # the same + the browser tests of app.js (Task 24);
                            # the first run downloads Chromium (needs internet)
./mvnw spring-boot:run      # run the app

# Create a user, or reset a password, then exit. There is no user
# management screen; this is how the first ΔΙΑΧΕΙΡΙΣΤΗΣ is made.
./mvnw spring-boot:run -Dspring-boot.run.profiles=create-user \
  -Dspring-boot.run.arguments="--user.username=<όνομα> '--user.full-name=<ονοματεπώνυμο>' [--user.role=ΥΠΑΛΛΗΛΟΣ]"
# The inner quotes matter: the plugin splits the arguments on spaces.
# The password is asked for twice in the terminal, without showing it
# (Task 22a); --user.password is refused. Run it in a terminal: with the
# input or output redirected there is no console, and it stops.

# One-off Excel migration into the configured database, then exit.
# Refuses a database that already has customers or vehicles unless
# --import.allow-existing-data=true is added (NOTES "Import risks").
./mvnw spring-boot:run -Dspring-boot.run.profiles=import \
  -Dspring-boot.run.arguments="--import.customers-file=<customers.xlsx> --import.archive-file=<archive.xlsx>"
```
