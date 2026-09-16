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
