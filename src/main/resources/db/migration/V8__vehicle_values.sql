-- The vehicles already stored get the values of the lists (Task 23a): the
-- mapping the Excel import applies from now on (VehicleValues), here in SQL,
-- once. Brands from vehicle_brand (V7), through their synonyms; category,
-- Euro and colour from the lists of VehicleValues, copied below as they are
-- today; seats 0 become empty, as the import stores them. A value that
-- matches nothing stays as it is, «Ι.Χ.» in the category included: it may be
-- M1 or N1 (decision 6). VehicleValuesMigrationTest checks that each value
-- ends as VehicleValues maps it.
--
-- Compared as the application compares: case and accents aside, with the
-- expression of search_normalized (V2, V6), whose Java twin is
-- TextNormalizationUtils.normalizeText; category and Euro also without
-- spaces or dashes and with the Greek letters that look Latin as Latin, as
-- normalizePlate does (SPEC §6).
--
-- As in V6: a bulk UPDATE bypasses the JPA audit listener, so every vehicle
-- changed here gets its UPDATE row in audit_log, in the listener's shape
-- (only the columns that changed, a change to empty as JSON null, no user),
-- and version and updated_at move, so a form opened before the migration
-- reports a conflict instead of saving over it.

CREATE FUNCTION pg_temp.normalized(value text) RETURNS text
    LANGUAGE sql IMMUTABLE
    RETURN upper(public.immutable_unaccent(btrim(value)) COLLATE pg_unicode_fast);

CREATE FUNCTION pg_temp.as_plate(value text) RETURNS text
    LANGUAGE sql IMMUTABLE
    RETURN translate(regexp_replace(pg_temp.normalized(value), '[[:space:] ‐-―-]', '', 'g'),
                     'ΑΒΕΖΗΙΚΜΝΟΡΤΥΧ', 'ABEZHIKMNOPTYX');

CREATE TEMPORARY TABLE category_key ON COMMIT DROP AS
SELECT pg_temp.as_plate(category) AS key, category
  FROM unnest(ARRAY['M1', 'M2', 'M3', 'N1', 'N2', 'N3', 'O1', 'O2', 'O3', 'O4',
                    'L1e', 'L2e', 'L3e', 'L4e', 'L5e', 'L6e', 'L7e', 'T']) AS category;

CREATE TEMPORARY TABLE colour_key ON COMMIT DROP AS
SELECT pg_temp.normalized(colour) AS key, colour
  FROM unnest(ARRAY['Λευκό', 'Μαύρο', 'Γκρι', 'Ασημί', 'Μπλε', 'Κόκκινο', 'Πράσινο', 'Κίτρινο',
                    'Πορτοκαλί', 'Καφέ', 'Μπεζ', 'Μπορντό', 'Μωβ', 'Ροζ', 'Χρυσαφί']) AS colour
UNION ALL
SELECT pg_temp.normalized(synonym), colour
  FROM (VALUES ('ΑΣΠΡΟ', 'Λευκό'), ('ΑΣΗΜΕΝΙΟ', 'Ασημί'), ('ΓΚΡΙΖΟ', 'Γκρι'), ('ΚΑΦΕΤΙ', 'Καφέ'),
               ('ΧΡΥΣΟ', 'Χρυσαφί'), ('ΜΟΒ', 'Μωβ')) AS synonym (synonym, colour);

CREATE TEMPORARY TABLE brand_key ON COMMIT DROP AS
SELECT DISTINCT pg_temp.normalized(spelling) AS key, name
  FROM vehicle_brand, unnest(array_append(synonyms, name::text)) AS spelling;

WITH keyed AS (
    SELECT id, brand, category, color, emission_standard, seats,
           pg_temp.normalized(brand) AS brand_key,
           pg_temp.as_plate(category) AS category_key,
           pg_temp.as_plate(emission_standard) AS euro_key,
           -- "ΛΕΥΚΟ / ΜΑΥΡΟ" and "ΛΕΥΚΟ-ΜΑΥΡΟ" are one pair of colours.
           regexp_replace(pg_temp.normalized(color), '\s*[-/]\s*', '-', 'g') AS colour_key
      FROM vehicle
),
mapped AS (
    SELECT k.*,
           coalesce(b.name, k.brand) AS new_brand,
           coalesce(c.category, k.category) AS new_category,
           coalesce(CASE WHEN k.euro_key = 'ZEV' THEN 'ZEV'
                         -- A letter must follow the number: EURO6DTEMP is Euro 6, EURO61 nothing.
                         WHEN k.euro_key ~ '^EURO[1-6]([A-Z][A-Z0-9]*)?$' THEN 'Euro ' || substr(k.euro_key, 5, 1)
                    END, k.emission_standard) AS new_emission_standard,
           coalesce(CASE WHEN k.colour_key = pg_temp.normalized('Πολύχρωμο') THEN 'Πολύχρωμο'
                         WHEN position('-' IN k.colour_key) = 0 THEN first_colour.colour
                         -- Two different colours; three or more stay as they are.
                         WHEN length(k.colour_key) - length(replace(k.colour_key, '-', '')) = 1
                              AND first_colour.colour <> second_colour.colour
                             THEN first_colour.colour || '-' || second_colour.colour
                    END, k.color) AS new_color,
           nullif(k.seats, 0) AS new_seats
      FROM keyed k
      LEFT JOIN brand_key b ON b.key = k.brand_key
      LEFT JOIN category_key c ON c.key = k.category_key
      LEFT JOIN colour_key first_colour ON first_colour.key = split_part(k.colour_key, '-', 1)
      LEFT JOIN colour_key second_colour ON second_colour.key = split_part(k.colour_key, '-', 2)
),
changed AS (
    SELECT * FROM mapped
     WHERE new_brand <> brand OR new_category <> category OR new_color <> color
        OR new_emission_standard IS DISTINCT FROM emission_standard OR new_seats IS DISTINCT FROM seats
),
logged AS (
    INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_values, new_values)
    SELECT NULL, 'UPDATE', 'Vehicle', c.id, diff.old_values, diff.new_values
      FROM changed c
      CROSS JOIN LATERAL (
          SELECT jsonb_object_agg(column_name, old_value) AS old_values,
                 jsonb_object_agg(column_name, new_value) AS new_values
            FROM (VALUES ('brand', to_jsonb(c.brand), to_jsonb(c.new_brand)),
                         ('category', to_jsonb(c.category), to_jsonb(c.new_category)),
                         ('color', to_jsonb(c.color), to_jsonb(c.new_color)),
                         ('emission_standard', to_jsonb(c.emission_standard), to_jsonb(c.new_emission_standard)),
                         ('seats', to_jsonb(c.seats), to_jsonb(c.new_seats)))
                 AS column_change (column_name, old_value, new_value)
           WHERE old_value IS DISTINCT FROM new_value
      ) AS diff
)
UPDATE vehicle v
   SET brand = c.new_brand,
       category = c.new_category,
       color = c.new_color,
       emission_standard = c.new_emission_standard,
       seats = c.new_seats,
       version = v.version + 1,
       updated_at = now()
  FROM changed c
 WHERE v.id = c.id;

-- The temporary tables go with the transaction; the functions would outlive
-- it in the migration's session.
DROP FUNCTION pg_temp.as_plate(text);
DROP FUNCTION pg_temp.normalized(text);
