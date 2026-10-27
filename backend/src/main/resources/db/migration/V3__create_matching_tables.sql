ALTER TABLE bank_transaction
    ADD COLUMN match_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    ADD CONSTRAINT ck_bank_transaction_match_status
        CHECK (match_status IN ('UNMATCHED', 'SUGGESTED', 'MATCHED', 'IGNORED'));

CREATE INDEX ix_bank_transaction_match_status ON bank_transaction (match_status);

-- a suggestion links one transaction to one or more invoices (more than one for combined payments)
CREATE TABLE match_suggestion (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT      NOT NULL REFERENCES bank_transaction (id) ON DELETE CASCADE,
    rule           VARCHAR(30) NOT NULL,
    score          INT         NOT NULL,
    confidence     VARCHAR(10) NOT NULL,
    reasons        JSONB       NOT NULL DEFAULT '[]',
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by     BIGINT REFERENCES app_user (id),
    decided_at     TIMESTAMPTZ,
    CONSTRAINT ck_match_suggestion_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_match_suggestion_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_match_suggestion_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

CREATE INDEX ix_match_suggestion_transaction ON match_suggestion (transaction_id);

CREATE TABLE match_suggestion_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    suggestion_id BIGINT         NOT NULL REFERENCES match_suggestion (id) ON DELETE CASCADE,
    invoice_id    BIGINT         NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    amount        NUMERIC(19, 2) NOT NULL,
    CONSTRAINT ck_match_suggestion_item_amount CHECK (amount > 0)
);

CREATE INDEX ix_match_suggestion_item_invoice ON match_suggestion_item (invoice_id);

-- confirmed money flow from a transaction to an invoice; invoice.paid_amount is the sum of these
CREATE TABLE allocation (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT         NOT NULL REFERENCES bank_transaction (id),
    invoice_id     BIGINT         NOT NULL REFERENCES invoice (id),
    amount         NUMERIC(19, 2) NOT NULL,
    source         VARCHAR(10)    NOT NULL,
    suggestion_id  BIGINT REFERENCES match_suggestion (id) ON DELETE SET NULL,
    created_by     BIGINT REFERENCES app_user (id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_allocation_amount CHECK (amount > 0),
    CONSTRAINT ck_allocation_source CHECK (source IN ('AUTO', 'MANUAL'))
);

CREATE INDEX ix_allocation_transaction ON allocation (transaction_id);
CREATE INDEX ix_allocation_invoice ON allocation (invoice_id);
