-- Plates are stored without dashes or spaces (Task 14): ΝΚΝ-7777 -> ΝΚΝ7777.
-- The letters are left exactly as they are.
--
-- The separators are the ones TextNormalizationUtils.stripPlateSeparators
-- removes: white space, the no-break space, and the dash characters
-- (hyphen-minus, and U+2010..U+2015). plate_normalized already had them
-- stripped, so it, its unique index and search_normalized do not change.
UPDATE vehicle
SET plate = regexp_replace(plate, '[[:space:] ‐-―-]', '', 'g')
WHERE plate ~ '[[:space:] ‐-―-]';
