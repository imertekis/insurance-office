-- Remaining tables (Task 3): vehicle, ownership, policy, intermediary,
-- app_user and audit_log. customer already exists (V1/V2).
--
-- Hard delete only (DECISIONS §4): no deleted_at column anywhere and no
-- partial unique indexes. Recovery goes through audit_log.

CREATE TABLE intermediary (
    id              BIGSERIAL    PRIMARY KEY,
    full_name       VARCHAR(200) NOT NULL,
    registry_number VARCHAR(50),
    phone           VARCHAR(10),
    email           VARCHAR(255),
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX idx_intermediary_full_name ON intermediary (full_name);

CREATE TABLE vehicle (
    id                  BIGSERIAL     PRIMARY KEY,
    vin                 VARCHAR(17)   NOT NULL,
    plate               VARCHAR(10)   NOT NULL,
    -- Filled by Vehicle's @PrePersist/@PreUpdate via TextNormalizationUtils,
    -- not by the database: the Greek->Latin mapping is awkward in SQL.
    plate_normalized    VARCHAR(10)   NOT NULL,
    brand               VARCHAR(50)   NOT NULL,
    model               VARCHAR(100)  NOT NULL,
    first_registration  DATE          NOT NULL,
    license_issue_date  DATE,
    category            VARCHAR(10)   NOT NULL,
    usage_type          VARCHAR(20)   NOT NULL
                                      CHECK (usage_type IN ('ΕΙΧ', 'ΦΙΧ', 'ΔΧ', 'ΤΑΞΙ', 'ΛΕΩΦΟΡΕΙΟ')),
    color               VARCHAR(50)   NOT NULL,
    seats               SMALLINT,
    -- Nullable: an electric vehicle has no engine capacity. A 0 in the Excel
    -- is stored as NULL so it cannot skew averages or sorting.
    engine_cc           INTEGER       CHECK (engine_cc IS NULL OR engine_cc > 0),
    power_kw            NUMERIC(6,2)  NOT NULL,
    fuel_type           VARCHAR(20)   NOT NULL
                                      CHECK (fuel_type IN ('ΒΕΝΖΙΝΗ', 'ΠΕΤΡΕΛΑΙΟ', 'ΥΒΡΙΔΙΚΟ',
                                                           'ΗΛΕΚΤΡΙΣΜΟΣ', 'LPG', 'CNG')),
    engine_number       VARCHAR(50),
    co2                 INTEGER,
    emission_standard   VARCHAR(20),
    weight_kg           INTEGER,
    -- Snapshot of the address on the licence document (C.1.3). Deliberately
    -- not synchronised with the owner's current address.
    license_street      VARCHAR(200),
    license_city        VARCHAR(100),
    license_postal_code VARCHAR(5),
    -- Same shape as customer.search_normalized (V2), so that
    -- TextNormalizationUtils.normalizeText() matches both.
    search_normalized   TEXT GENERATED ALWAYS AS (
        upper(
            public.immutable_unaccent(
                rtrim(
                    coalesce(plate_normalized || ' ', '') ||
                    coalesce(vin              || ' ', '') ||
                    coalesce(brand            || ' ', '') ||
                    coalesce(model, '')
                )
            ) COLLATE pg_unicode_fast
        )
    ) STORED,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_vehicle_vin ON vehicle (vin);
CREATE UNIQUE INDEX idx_vehicle_plate ON vehicle (plate_normalized);
CREATE INDEX idx_vehicle_search ON vehicle USING gin (search_normalized gin_trgm_ops);

CREATE TABLE ownership (
    id          BIGSERIAL    PRIMARY KEY,
    -- Ownership only exists for a vehicle and a customer, so it follows them
    -- into a hard delete instead of blocking it.
    vehicle_id  BIGINT       NOT NULL REFERENCES vehicle (id) ON DELETE CASCADE,
    customer_id BIGINT       NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    percentage  NUMERIC(5,2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    is_primary  BOOLEAN      NOT NULL,
    from_date   DATE,
    to_date     DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ownership_period_ordered CHECK (to_date IS NULL OR from_date IS NULL OR to_date >= from_date)
);

-- NULLS NOT DISTINCT so the constraint still holds for the v1 case where
-- from_date is left empty; the default would treat every NULL as unique.
CREATE UNIQUE INDEX idx_ownership_vehicle_customer_from
    ON ownership (vehicle_id, customer_id, from_date) NULLS NOT DISTINCT;
CREATE INDEX idx_ownership_vehicle ON ownership (vehicle_id);
CREATE INDEX idx_ownership_customer ON ownership (customer_id);

CREATE TABLE policy (
    id                BIGSERIAL     PRIMARY KEY,
    policy_number     VARCHAR(30)   NOT NULL,
    vehicle_id        BIGINT        NOT NULL REFERENCES vehicle (id) ON DELETE CASCADE,
    insurance_company VARCHAR(100)  NOT NULL,
    intermediary_id   BIGINT        REFERENCES intermediary (id),
    start_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    -- Money is numeric, never text. 6- and 12-month policies both exist, so
    -- no duration is implied here.
    premium           NUMERIC(10,2) NOT NULL,
    surcharge         BOOLEAN       NOT NULL DEFAULT FALSE,
    surcharge_type    VARCHAR(30)   CHECK (surcharge_type IN ('ΝΕΟΣ_ΟΔΗΓΟΣ', 'ΗΛΙΚΙΑΣ', 'ΑΛΛΟ')),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT policy_end_after_start CHECK (end_date > start_date)
);

CREATE UNIQUE INDEX idx_policy_number ON policy (policy_number);
-- Feeds the expiry dashboard (SPEC §7.1).
CREATE INDEX idx_policy_end_date ON policy (end_date);
CREATE INDEX idx_policy_vehicle ON policy (vehicle_id);

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ΥΠΑΛΛΗΛΟΣ', 'ΔΙΑΧΕΙΡΙΣΤΗΣ')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    TIMESTAMP
);

CREATE UNIQUE INDEX idx_app_user_username ON app_user (username);

CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    -- The log outlives the user: deleting an account must not erase who did what.
    user_id     BIGINT      REFERENCES app_user (id) ON DELETE SET NULL,
    action      VARCHAR(10) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'VIEW')),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT,
    old_values  JSONB,
    new_values  JSONB,
    ip_address  VARCHAR(45),
    "timestamp" TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log ("timestamp");
