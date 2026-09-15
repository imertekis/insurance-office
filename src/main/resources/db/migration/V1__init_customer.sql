-- Customer table only (Task 1). search_normalized is added in V2 (Task 2).

CREATE TABLE customer (
    id           BIGSERIAL    PRIMARY KEY,
    tax_id       VARCHAR(9),
    entity_type  VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL'
                              CHECK (entity_type IN ('INDIVIDUAL', 'COMPANY')),
    last_name    VARCHAR(100) NOT NULL,
    first_name   VARCHAR(100),
    father_name  VARCHAR(100),
    birth_date   DATE,
    license_date DATE,
    tax_office   VARCHAR(100),
    street       VARCHAR(200),
    city         VARCHAR(100),
    postal_code  VARCHAR(5),
    mobile       VARCHAR(10),
    phone        VARCHAR(10),
    email        VARCHAR(255),
    notes        TEXT,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Nullable but unique: PostgreSQL allows many NULLs (DECISIONS §2).
CREATE UNIQUE INDEX idx_customer_tax_id ON customer (tax_id);
