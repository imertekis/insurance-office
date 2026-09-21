-- The customer list and the search sort by name in Greek alphabetical order
-- (Task 15).
--
-- The database default does not do that. pg_unicode_fast compares code
-- points, which puts the accented capitals (Ά, Έ, Ή, Ώ) before Α and every
-- lower-case name after every capital; the libc default depends on the
-- operating system's locale, and the C locale compares bytes. So the sort
-- key carries an ICU collation of its own, whatever the database was
-- created with. Accents and case are ignored at the first level and only
-- break ties, so «Άγγελος» sorts with Α, between «Αβραμίδης» and «Αλεξίου».
--
-- Like search_normalized, it is computed by PostgreSQL, so that writes that
-- bypass JPA also fill it. Last name first, as the name is written on the
-- cards; a customer without a first name is just the last name. Needs a
-- PostgreSQL built with ICU, as the official image is: without it this
-- migration fails at once, instead of the list coming out in the wrong order.
ALTER TABLE customer
    ADD COLUMN name_sort TEXT COLLATE "el-GR-x-icu" GENERATED ALWAYS AS (
        last_name || coalesce(' ' || first_name, '')
    ) STORED;

-- id last: the list breaks ties by id, so its pages never overlap.
CREATE INDEX idx_customer_name_sort ON customer (name_sort, id);
