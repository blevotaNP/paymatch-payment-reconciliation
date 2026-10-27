-- optimistic locking: two people deciding about the same payment at once must not both succeed
ALTER TABLE bank_transaction
    ADD COLUMN version      BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ignore_reason VARCHAR(30),
    ADD COLUMN ignore_note   VARCHAR(500);

ALTER TABLE allocation
    ADD COLUMN note VARCHAR(500);

CREATE TABLE audit_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id     BIGINT REFERENCES app_user (id),
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(20)  NOT NULL,
    entity_id   BIGINT,
    summary     VARCHAR(500) NOT NULL,
    -- invoices touched by the event, so an invoice's history can be shown without parsing details
    invoice_ids BIGINT[]     NOT NULL DEFAULT '{}',
    details     JSONB        NOT NULL DEFAULT '{}'
);

CREATE INDEX ix_audit_event_occurred_at ON audit_event (occurred_at DESC);
CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id);
CREATE INDEX ix_audit_event_invoice_ids ON audit_event USING GIN (invoice_ids);
