-- Search normalization for customer (Task 2).
--
-- search_normalized is computed by PostgreSQL, not by Java, so that every
-- write fills it, including writes that bypass JPA (Excel import, manual SQL,
-- migrations). Requires PostgreSQL 18+ (pg_unicode_fast collation).

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- unaccent() is only STABLE (its dictionary can change), so PostgreSQL does
-- not allow it in generated columns or indexes. This wrapper pins the
-- dictionary by schema-qualified name and is declared IMMUTABLE.
-- If the unaccent dictionary is ever changed, rewrite the customer rows
-- so that search_normalized is recomputed.
CREATE FUNCTION public.immutable_unaccent(input text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, input);

-- Upper-casing uses the built-in pg_unicode_fast collation so the stored
-- value does not depend on the server's OS locale or glibc version.
-- TextNormalizationUtils.normalizeText() must produce the same result for
-- search input (checked by SearchNormalizationConsistencyTest).
ALTER TABLE customer
    ADD COLUMN search_normalized TEXT GENERATED ALWAYS AS (
        upper(
            public.immutable_unaccent(
                rtrim(
                    coalesce(last_name  || ' ', '') ||
                    coalesce(first_name || ' ', '') ||
                    coalesce(tax_id     || ' ', '') ||
                    coalesce(mobile     || ' ', '') ||
                    coalesce(phone      || ' ', '') ||
                    coalesce(email, '')
                )
            ) COLLATE pg_unicode_fast
        )
    ) STORED;

CREATE INDEX idx_customer_search ON customer
    USING gin (search_normalized gin_trgm_ops);
