# Review 09: Tasks 18, 19, 20, 21a, 21b

## Task 18: Unique Constraints
* **Unique indices mapped:** Yes. All business entity indices (`idx_customer_tax_id`, `idx_vehicle_vin`, `idx_vehicle_plate`, `idx_policy_number`, `idx_ownership_vehicle_customer_from`, `idx_intermediary_full_name`) are correctly mapped in `UniqueConstraint.java`. The only unique index not mapped is `idx_app_user_username`, but since user creation is handled exclusively via a CLI command and not the web interface, it does not affect the UI.
* **Violations turned to messages:** Yes. All relevant write paths (`CustomerController`, `VehicleController`, `PolicyController`, `OwnershipController` in their POST methods) catch `DataIntegrityViolationException` and pass it to `FormErrors.show()`, successfully mapping the exception to a field error. The `ExcelImporterService` also catches this exception and correctly translates it into a row rejection message. 
* **Any path that still reaches a 500?** No. All write paths available to the user safely handle constraint violations without reaching a 500 server error. 

## Task 19: Reopen Rule
* **Picks the right row:** Yes. The method `removedOn` calls `findEndingOn(..., transferDate)`. The query is specifically ordered by `fromDate desc nulls last, id desc`. Because it stores the entities using `putIfAbsent()`, it correctly retains the first (most recent) matching row, appropriately treating an empty start date (`nulls last`) as the oldest, and defaulting to the largest ID for ties.
* **Never reopens a row it should not:** Yes. It restricts processing only to the members of `joining` (owners being explicitly added who are not currently active), and it enforces a strict match for `toDate = transferDate`. An owner with a different removal date or an actively current owner is never falsely reopened.

## Task 21a: Search Suggestions
* **Matches the first rows:** Yes. Both `SearchService.search()` and `suggest()` share the exact same underlying `find(input)` query, which caps the lists to `MAX_HITS` (50). The `suggest` method slices from these identical, pre-ordered lists (apportioning up to 4 per group, shifting remainders if one group has fewer hits, up to an 8 item cap).
* **Is the total right?** Yes. The `total` uses the sum of the retrieved hits, which perfectly matches the full page total. It also properly appends a `+` to the total string when the global `truncated` flag triggers for results > 50, aligning identically with the UI's display logic.

## Tasks 20 and 21b: JavaScript State
* **Shares logic (Task 20):** No wrong state found. The application effectively handles rounding using arbitrary precision `BigInt` implementations for summing percentages in cents (preventing floating-point math inaccuracies). Auto-fill correctly limits propagation if the typed value is `0`, `>= 100`, or has `> 2` decimals, ignoring invalid shares and maintaining form integrity.
* **Suggestion list (Task 21b):** No wrong state found. It robustly handles race conditions and debouncing using `setTimeout` (250ms delay) and clears previous requests using an `AbortController`. It prevents erroneous focus issues and unexpected blurs using `mousedown.preventDefault()` on the list, and appropriately handles `pageshow` (bfcache).

## Gaps in Test Coverage
* There are zero automated tests for the frontend JavaScript logic in `app.js`. While the backend controllers and services are tested exhaustively via integration and unit tests, the client-side behaviors (the auto-calculation of ownership shares and the live suggestion fetch/DOM injection routines) have no automated validation (e.g. no Jest or Selenium/Playwright tests exist to verify JS interactions).
