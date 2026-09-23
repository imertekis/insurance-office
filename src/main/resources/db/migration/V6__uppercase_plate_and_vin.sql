-- Plates and VINs are stored in capitals (Task 17, DECISIONS §5). The plate
-- also loses its accents and keeps its alphabet as typed: νκν7777 -> ΝΚΝ7777,
-- άβε1234 -> ΑΒΕ1234, abe1234 -> ABE1234 (still Latin). The application does
-- the same on every save from now on (TextNormalizationUtils.storedPlate and
-- storedVin); this brings the rows already stored in line.
--
-- Accent removal and upper-casing use the expression of search_normalized
-- (V2, V3): immutable_unaccent, then upper() under the built-in
-- pg_unicode_fast collation, whatever the database's own collation is (under
-- C, upper() would leave Greek letters alone). SearchNormalizationConsistencyTest
-- shows that this expression gives what TextNormalizationUtils.normalizeText
-- gives in Java.
--
-- plate_normalized is already upper-cased and unaccented, so neither it nor
-- its unique index moves, and no two plates can collide. The VIN index is
-- case-sensitive: two vehicles whose VINs differ only in case would collide.
-- The migration then stops and names them, rather than merge anything.
--
-- A bulk UPDATE bypasses the JPA audit listener (NOTES, REVIEW-03 finding 2),
-- so every vehicle changed here gets its UPDATE row in audit_log, in the
-- listener's shape: entity_type Vehicle, only the columns that changed, no
-- user (as for the import). version and updated_at move as on any update, so
-- a form opened before the migration reports a conflict instead of saving
-- over it.

DO $$
DECLARE
    clashes TEXT;
BEGIN
    SELECT string_agg(format('%s (οχήματα %s)', upper_vin, ids), '; ' ORDER BY upper_vin)
      INTO clashes
      FROM (SELECT upper(vin COLLATE pg_unicode_fast) AS upper_vin,
                   string_agg(id::text, ', ' ORDER BY id) AS ids
              FROM vehicle
             GROUP BY 1
            HAVING count(*) > 1) AS clash;
    IF clashes IS NOT NULL THEN
        RAISE EXCEPTION 'V6: ίδιο VIN σε διαφορετική γραφή (πεζά/κεφαλαία): %. Ενοποιήστε αυτά τα οχήματα με το χέρι και ξεκινήστε ξανά την εφαρμογή.', clashes;
    END IF;
END $$;

WITH fixed AS (
    SELECT id,
           plate AS old_plate,
           upper(public.immutable_unaccent(plate) COLLATE pg_unicode_fast) AS new_plate,
           vin AS old_vin,
           upper(vin COLLATE pg_unicode_fast) AS new_vin
      FROM vehicle
),
changed AS (
    SELECT * FROM fixed WHERE new_plate <> old_plate OR new_vin <> old_vin
),
logged AS (
    INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_values, new_values)
    SELECT NULL, 'UPDATE', 'Vehicle', id,
           jsonb_strip_nulls(jsonb_build_object(
               'vin', CASE WHEN new_vin <> old_vin THEN old_vin END,
               'plate', CASE WHEN new_plate <> old_plate THEN old_plate END)),
           jsonb_strip_nulls(jsonb_build_object(
               'vin', CASE WHEN new_vin <> old_vin THEN new_vin END,
               'plate', CASE WHEN new_plate <> old_plate THEN new_plate END))
      FROM changed
)
UPDATE vehicle v
   SET plate = c.new_plate,
       vin = c.new_vin,
       version = v.version + 1,
       updated_at = now()
  FROM changed c
 WHERE v.id = c.id;
