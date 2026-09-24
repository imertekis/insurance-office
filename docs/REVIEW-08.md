# Review of Tasks 16e and 16f

## Correctness Findings

### OwnershipService
- **Matches Definition:** The "rows differ from the saved owners" logic correctly implements most of the rules from 16f-2. It compares shares numerically (`savedShare.compareTo(typedShare) != 0`), flags unreadable shares correctly by catching `null` from `number()`, and ignores the transfer date.
- **Wrong Answer Case (Bug):** The logic gives the wrong answer when the form contains a duplicate customer ID, but the total number of rows matches the saved state (e.g., replacing a second owner with a duplicate of the first owner, with matching shares). Because it loops over the submitted IDs and compares each to `savedShares.get(id)`, it will check the duplicated owner's share twice and fail to notice that the other saved owner is missing. It will incorrectly return `false` (no differences). As a result, when the form reloads due to the duplicate validation error, it will lack `data-unsaved="true"`, and the user can leave the page without warning.

### app.js
- **Block legitimate first submit?** No. The single-submit logic correctly protects against double submissions without interfering with legitimate ones. It properly skips GET forms and respects `event.defaultPrevented` (e.g., if HTML5 validation fails). If the user returns via the browser's "Back" button, the `pageshow` listener successfully re-enables the form.
- **Lose the pressed button's name/value?** No. The script uses `setTimeout(..., 0)` to disable the buttons. This pushes the disabling operation to the task queue, allowing the browser's synchronous `submit` event default action (which gathers the form data, including the pressed button's name and value) to complete first.

### Flash Messages
- **Exactly one message?** Yes. Every save, delete, and renewal path across the controllers sets exactly one flash message. `OwnershipController` correctly restricts the message only to the `action=save` path and not intermediate POST actions (add, remove, search). `PolicyController` correctly branches to `Notice.RENEWED` for renewals.

### Gaps in Test Coverage
- **Overall Coverage:** Test coverage is exceptionally thorough, with `FlashMessageTest` covering all success/delete notifications and `OwnershipFormTest` verifying complex `data-unsaved` rules.
- **Gap:** There is no test verifying that `data-unsaved` is true when the form is submitted with a duplicate customer ID. This gap corresponds directly to the bug identified in `OwnershipService`.

## Response

The finding is real against the definition in Task 16f-2 («άλλο σύνολο πελατών»): `differsFromSaved` compared the size of the submitted list, not the set of customers. It is fixed. The UI cannot produce the input, though; only a hand-made request can.

- **Can a duplicate customer id come from the UI?** No.
  - The search results leave out whoever is already in the rows (`OwnershipController.candidates`), and «Προσθήκη» of a customer already present returns the rows unchanged (`withAdded`).
  - Enter in the form presses the hidden `action=refresh` button, which adds nobody.
  - A double click, the Back button or a second tab cannot either: nothing is kept on the server between requests, and a page never shows both a row for a customer and an «Προσθήκη» for them.
  - Only a hand-made request (developer tools, curl) sends two `customerId` fields with the same id.
- **What «Αποθήκευση» does with it:** refuses it with «Ο ίδιος πελάτης εμφανίζεται δύο φορές.» at the top of the page. Nothing is saved, merged or dropped, so no row wins, whatever the shares or primary-owner flags.
- **After the refused save:** the page shows the rows as sent, the duplicate included. They are not what would be saved (nothing would be), and the saved owners are untouched. So nothing could be lost, but the page lacked `data-unsaved="true"`, against the 16f-2 rule that a refused save with different rows asks before leaving.

**Fix:** `differsFromSaved` returns true as soon as the submitted ids contain a duplicate: the saved owners have one current row per customer, so such a list never matches them. The javadoc says so. `OwnershipFormTest.marksTheFormWithACustomerTwice` covers the reviewer's case (saved Αλεξίου 50% primary and Βασιλείου 50%, sent as Αλεξίου 50% twice):
- after a search, the page is marked;
- after a save, it is refused with the message above and still marked;
- the saved owners are unchanged.

The test fails without the fix. It also covers the refusal of a duplicate itself, which no test checked before.
