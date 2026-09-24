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
