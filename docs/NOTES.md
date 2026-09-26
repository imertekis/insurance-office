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

## Search suggestions: timing and indexes (Task 21a)

**Conclusion: no new index.** Every input answers in well under the
100 ms target (worst p95 24 ms, most 3–10 ms), so the schema is unchanged.

**Setup.** A throwaway `postgres:18` container with the image's defaults
(`shared_buffers` 128 MB), migrated by the application itself, on the
same machine as the application: Intel i5-6500 (4 cores, 3.2 GHz), 23 GB.
Synthetic data made with `generate_series` at the volume of SPEC §14.1:
8,000 customers, 10,000 vehicles, 11,000 ownerships, 25,000 policies, then
`VACUUM ANALYZE`. Common Greek surnames skewed towards the first ones (so
«παπ» matches 23% of customers), 55% with an email, 90% with a mobile, a
fleet company with 152 vehicles, plates half Greek and half Latin, 60% in
five local Η- series, and the `ZZZ` filler in Volkswagen, Seat and Skoda
VINs. The script is not in the repository.

**Timing.** The server's own time per request (Tomcat access log, `%D`),
logged in and with `Accept: application/json`: 30 warm-up rounds, then 50
calls per input, the inputs interleaved. In ms:

| Input | Kind | Matches (customers / vehicles) | Median | p95 | Max |
|---|---|---|---|---|---|
| «παπ» | common surname prefix | 1,871 / 0 | 9.2 | 12.9 | 14.9 |
| «παπαδ» | longer prefix | 1,027 / 0 | 9.5 | 11.6 | 13.7 |
| «toy» | brand | 0 / 1,517 | 5.8 | 7.0 | 10.9 |
| «ηοκ» | plate start, Greek | 0 / 76 | 5.8 | 7.7 | 14.5 |
| «hok» | plate start, Latin | 0 / 76 | 5.7 | 8.7 | 14.1 |
| «com» | email domain, the broadest | 3,392 / 0 | 5.4 | 7.2 | 12.5 |
| «697» | three digits, both groups | 1,110 / 37 | 7.1 | 8.8 | 10.9 |
| «zzz» | VIN filler | 0 / 1,627 | 5.8 | 6.7 | 7.5 |
| «α β» | two one-letter words | 690 / 6,339 | 22.1 | 24.0 | 25.3 |
| «101057138» | ΑΦΜ | 1 / 0 | 2.9 | 4.0 | 4.2 |
| «ΜΟΟ-9733» | plate, Greek | 0 / 1 | 3.1 | 5.6 | 7.8 |
| «PKP3797» | plate, Latin | 0 / 1 | 3.1 | 5.2 | 8.6 |
| «2100001001» | policy number, and landline | 0 / 1 | 4.6 | 6.7 | 8.6 |
| «6941121570» | mobile | 1 / 0 | 4.0 | 5.2 | 10.7 |
| «ψωξ» | no results | 0 / 0 | 3.7 | 6.5 | 10.3 |
| «πα» | 2 characters, no database | — | 1.0 | 2.0 | 4.0 |

The first request after the application starts took 197 ms (cold JVM),
the first call of each other input 8–57 ms; from then on, the table.

**Plans.** `EXPLAIN (ANALYZE, BUFFERS)` of the endpoint's own statements,
with their bind values, logged by `auto_explain` on the 12th call of each
input, past the five calls after which pgjdbc switches to server-prepared
statements. PostgreSQL still planned each LIKE pattern on its own (the
plans show the pattern, not `$1`). All pages came from shared buffers (no
disk reads); `customer` and `vehicle` together are 18 MB.
- **Free text, customers.** Few matches: Bitmap Index Scan on
  `idx_customer_search` (GIN), then a sort by `name_sort`, ≤ 0.03 ms when
  nothing matches. Many matches: the planner walks `idx_customer_name_sort`
  in order and filters until it has 51 rows: «παπ» 4.4 ms (4,990
  buffers), «697» 3.2 ms, «com» 0.35 ms. Its worst case, matches only at
  the end of the alphabet («ψαρρ», with bitmap and sequential scans turned
  off to force the plan), scans the whole index: 7.1 ms.
- **Free text, vehicles.** A GIN bitmap (a BitmapOr with the plate form),
  or `idx_vehicle_plate` in order with an incremental sort by id when many
  match: under 0.6 ms.
- **One-letter words («α β»).** The slowest statement, 20.2 ms: a word
  under three characters gives the trigram index nothing to filter on, so
  the GIN scan returns every customer and 690 rows are sorted by the ICU
  collation. The only statement that grows with the whole customer table;
  by estimate, not measured, some ten times the volume of SPEC §14.1 would
  take it past the target.
- **Exact types.** `idx_customer_tax_id`, `idx_vehicle_plate`, and
  `idx_policy_number` then `vehicle_pkey`: ≤ 0.04 ms each. Landline and
  mobile have no index: a Seq Scan of `customer` (307 buffers), 1.1 ms.
- **The shown rows (`HitAssembler`, at most 8).** `idx_ownership_customer`
  for the vehicle counts, `idx_ownership_vehicle` for the primary owners:
  under 0.25 ms.

The remaining ~3 ms of each request is the application: session and
login check, Hibernate, JSON. Below three characters no statement runs
(1.0 ms, all application).

**`Accept: application/json` (done in Task 21b).** The header's script
sends it. With it, an expired session gets the redirect to the login page
and nothing is remembered; with the default `*/*`, Spring Security would
remember the suggestions address, and a login right after would open the
JSON. The Task 21b headless check logged out in a second tab, typed in
the first (no list), pressed Enter (the login page) and logged in: back
on the search page, not on the JSON.

## Deployment requirements

What the installation in the office must provide, recorded as decisions
come up; the deployment task starts from this list.

- **All access goes through Tailscale, also inside the office** (TASKS,
  Task 22, decision 9). SPEC §12 asks for HTTPS even on the LAN; the
  encryption of the connection is a deployment matter. Every browser, the
  office PCs included, reaches the application over Tailscale, whose
  WireGuard tunnel encrypts it, and the application is not reachable on the
  office LAN outside it. Until then, passwords cross the office network in
  the clear.

## Manual checks before deployment

What an automated run cannot show, to check by hand in the office's own
browsers and on its own screens before the app goes live. Grouped by task;
an item leaves this list once it has been checked.

### Task 16a
- **Vehicle or policy form:** save a date such as 03/04/2020, reopen the
  form and the card: it still reads 03/04/2020, never 04/03/2020.

### Task 16d-2
- **Any page, in a real browser tab:** the app's icon shows in the tab, in
  the browser's light and dark look.

### Task 16e
- **Any page, with the Greek keyboard layout:** "/" puts the cursor in the
  header's search box; in a field it types "/".
- **A vehicle card, dark theme, Ctrl+P:** it prints black on white, on one
  A4 page.

### Task 16f-2
- **A customer form after typing:** F5, Ctrl+W, «Πίσω» (Alt+←) and closing
  the window each make the browser ask before leaving.
- **New customer, from a second PC over the office network:** a double
  click on «Αποθήκευση» saves one customer, not two.

### Task 18
- **New customer, two tabs, the same ΑΦΜ saved at once:** the second shows
  «Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ.» beside the field, not an error
  page. The two saves must overlap in the database; the Task 18 check used
  a throwaway database whose inserts were slowed by a trigger.

### Task 19
- **Owners form, a vehicle with two owners:** remove the wrong owner and
  save, add them back with the same date and save. The card shows them once,
  as current, with their original start and no «Πρώην» row.

### Task 20
- **Owners form, «Πίσω».** The headless check (Chrome, DevTools protocol)
  never got the page back from the browser's cache: the app's pages are
  sent `no-store`, so «Πίσω» loaded them again from the server, with the
  saved shares and their total. The kept-page path was only run with a
  synthetic `pageshow` event. By hand, in each browser the office uses:
  type a share on a vehicle with three owners, follow a link, choose to
  leave, press «Πίσω». Either the shares come back as typed with their
  total, or the saved shares with theirs; never typed shares with the saved
  total. The buttons work.
- **Owners form, the office keyboard.** The check typed the decimal comma
  as a character. With the Greek layout, type «49,5» using the numeric
  keypad's decimal key: the other owner gets «50,5».
- **Owners form, a screen reader.** The total line under three or more
  owners is `aria-live="polite"`. With the screen reader the office may use
  (NVDA or Narrator), typing a share reads out the new total.
- **Owners form, a real phone.** At 375px the share fields sit in the
  table's own horizontal scroll (Task 16d-2), and the total line under it.
  On a phone: scroll the table to a share, type with the decimal keypad
  («inputmode»), and read the total.
- **Vehicle and customer cards, the office screens.** Former owners and
  expired policies are in Bootstrap's secondary text colour, measured at
  6.78:1 in the light theme and 7.29:1 in the dark one. On the office
  monitors, at their usual brightness, those rows still read easily, and
  are clearly fainter than current ones.

### Task 21a
- **Search suggestions, on the office server with the imported data.** The
  measurement («Search suggestions: timing and indexes», above) ran on a
  2015 desktop (i5-6500); the office server may be a Raspberry Pi 5, and
  the real names and emails are not the synthetic ones. Logged in, open `/search/suggestions?q=παπ` (or the office's most
  common surname prefix, and «com») and reload it five times; in the
  browser's developer tools, Network tab, the request's «Waiting for server
  response» stays under 100 ms after the first load. The first request
  after the application starts is slower (197 ms here) and does not count.

### Task 21b
- **Suggestions, in each browser the office uses.** The check ran in
  headless Chrome only, with key events that give each letter directly.
  With the Greek layout, type «Αλεξίου» with its accent (the dead key «΄»,
  then the letter): the list opens after the third letter and follows every
  letter typed, the accented one too. Then ↓ and ↑ move through the rows,
  Enter opens the chosen one, Esc closes the list and leaves the text, a
  click on a row opens it, a click elsewhere closes the list.
- **Suggestions, with a screen reader.** The field is a WAI-ARIA combobox
  and the list a listbox with groups. With the screen reader the office
  may use (NVDA or Narrator): the field is read as a combobox, collapsed
  or expanded as the list closes and opens; ↓ reads each row, name and
  second part, with its group («Πελάτες», «Οχήματα»), and the last one,
  «Όλα τα αποτελέσματα»; «Κανένα αποτέλεσμα» is read when nothing matches.
- **Suggestions, on a real phone.** The check emulated a 375px phone with
  touch, and stood in for the on-screen keyboard with a short window
  (375×400). On a phone, with the keyboard up: each row is two lines
  (CLAUDE.md, resolved conflict 9), the list ends above the keyboard and
  scrolls inside itself down to «Όλα τα αποτελέσματα», and a tap on a row
  opens its card without first closing the list. Also with the keyboard
  opening after the list, and in landscape.

## Open questions

Every decision still to be made in the project. CLAUDE.md points here; an
entry leaves this list when the decision is recorded (in DECISIONS, CLAUDE.md
or the task). Details that live elsewhere in this file are linked, not
repeated.

- **Tasks 25–27 (stubs) end with «Ανοιχτά ερωτήματα»** in `docs/TASKS.md`,
  kept there beside the task they belong to. To be decided after the first
  week of use in the office. (Tasks 22–24 have their decisions recorded.)
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
