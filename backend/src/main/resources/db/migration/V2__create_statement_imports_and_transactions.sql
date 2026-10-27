CREATE TABLE statement_import (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    bank_format     VARCHAR(30)  NOT NULL,
    account_iban    VARCHAR(34),
    period_from     DATE,
    period_to       DATE,
    file_sha256     VARCHAR(64)  NOT NULL,
    encoding        VARCHAR(30)  NOT NULL,
    total_rows      INT          NOT NULL,
    imported_count  INT          NOT NULL,
    duplicate_count INT          NOT NULL,
    skipped_count   INT          NOT NULL,
    skipped_rows    JSONB        NOT NULL DEFAULT '[]',
    warnings        JSONB        NOT NULL DEFAULT '[]',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    imported_by     BIGINT       NOT NULL REFERENCES app_user (id),
    imported_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    undone_by       BIGINT REFERENCES app_user (id),
    undone_at       TIMESTAMPTZ,
    CONSTRAINT ck_statement_import_status CHECK (status IN ('ACTIVE', 'UNDONE'))
);

CREATE INDEX ix_statement_import_imported_at ON statement_import (imported_at DESC);

CREATE TABLE bank_transaction (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id            BIGINT         NOT NULL REFERENCES statement_import (id),
    account_iban         VARCHAR(34),
    booking_date         DATE           NOT NULL,
    value_date           DATE,
    amount               NUMERIC(19, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    counterparty_name    VARCHAR(255),
    counterparty_account VARCHAR(34),
    title                VARCHAR(1000),
    bank_reference       VARCHAR(100),
    dedup_key            VARCHAR(64)    NOT NULL,
    raw_data             TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_bank_transaction_amount_not_zero CHECK (amount <> 0)
);

-- the database is the last line of defence against duplicated imports
CREATE UNIQUE INDEX ux_bank_transaction_dedup_key ON bank_transaction (dedup_key);
CREATE INDEX ix_bank_transaction_import ON bank_transaction (import_id);
CREATE INDEX ix_bank_transaction_booking_date ON bank_transaction (booking_date DESC);
