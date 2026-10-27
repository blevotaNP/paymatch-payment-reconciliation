CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE counterparty (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    nip        VARCHAR(10),
    iban       VARCHAR(34),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_counterparty_nip ON counterparty (nip) WHERE nip IS NOT NULL;
CREATE INDEX ix_counterparty_iban ON counterparty (iban);

CREATE TABLE invoice (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number          VARCHAR(64)    NOT NULL,
    counterparty_id BIGINT         NOT NULL REFERENCES counterparty (id),
    issue_date      DATE           NOT NULL,
    due_date        DATE           NOT NULL,
    gross_amount    NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'PLN',
    paid_amount     NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoice_gross_positive CHECK (gross_amount > 0),
    CONSTRAINT ck_invoice_paid_not_negative CHECK (paid_amount >= 0),
    CONSTRAINT ck_invoice_dates CHECK (due_date >= issue_date),
    CONSTRAINT ck_invoice_status CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERPAID'))
);

CREATE UNIQUE INDEX ux_invoice_number ON invoice (upper(number));
CREATE INDEX ix_invoice_counterparty ON invoice (counterparty_id);
CREATE INDEX ix_invoice_status_due ON invoice (status, due_date);
