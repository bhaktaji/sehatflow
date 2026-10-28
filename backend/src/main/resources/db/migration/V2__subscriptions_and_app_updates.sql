-- V2: Clinic Subscriptions, Razorpay Payment Transactions, Webhook Idempotency & In-App Release Manifests

CREATE TABLE clinic_subscriptions (
    id                   UUID PRIMARY KEY,
    clinic_id            UUID NOT NULL UNIQUE,
    plan_id              VARCHAR(64) NOT NULL,
    plan_name            VARCHAR(120) NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
    price_inr            BIGINT NOT NULL DEFAULT 0,
    currency             VARCHAR(8) NOT NULL DEFAULT 'INR',
    auto_renew           BOOLEAN NOT NULL DEFAULT FALSE,
    started_at           TIMESTAMPTZ NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    cancelled_at         TIMESTAMPTZ NULL,
    cancellation_reason  VARCHAR(240) NULL,
    last_transaction_id  VARCHAR(64) NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_subscription_status CHECK (status IN
        ('TRIAL', 'ACTIVE', 'CANCELLED_AT_PERIOD_END', 'PAST_DUE', 'EXPIRED', 'REVOKED_REFUNDED')),
    CONSTRAINT fk_subscription_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id)
);

CREATE TABLE payment_transactions (
    id                  VARCHAR(64) PRIMARY KEY,
    clinic_id           UUID NOT NULL,
    gateway_order_id    VARCHAR(80) NOT NULL UNIQUE,
    gateway_payment_id  VARCHAR(80) NULL UNIQUE,
    gateway_refund_id   VARCHAR(80) NULL,
    idempotency_key     VARCHAR(120) NOT NULL,
    plan_id             VARCHAR(64) NOT NULL,
    plan_name           VARCHAR(120) NOT NULL,
    amount_inr          BIGINT NOT NULL,
    amount_paise        BIGINT NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    status              VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    payment_method      VARCHAR(32) NOT NULL,
    masked_instrument   VARCHAR(120) NOT NULL,
    signature_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    gst_invoice_number  VARCHAR(64) NULL,
    failure_reason      VARCHAR(240) NULL,
    paid_at             TIMESTAMPTZ NULL,
    refunded_at         TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payment_txn_status CHECK (status IN
        ('CREATED', 'PENDING', 'CAPTURED', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_payment_txn_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id),
    CONSTRAINT uq_payment_clinic_idempotency UNIQUE (clinic_id, idempotency_key)
);
CREATE INDEX ix_payment_txn_clinic_created ON payment_transactions (clinic_id, created_at DESC);

CREATE TABLE payment_webhook_events (
    event_id            VARCHAR(120) PRIMARY KEY,
    event_type          VARCHAR(64) NOT NULL,
    gateway_order_id    VARCHAR(80) NOT NULL,
    gateway_payment_id  VARCHAR(80) NULL,
    processed_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_releases (
    version_code              INTEGER PRIMARY KEY,
    version_name              VARCHAR(32) NOT NULL,
    min_required_version_code INTEGER NOT NULL DEFAULT 1,
    is_mandatory              BOOLEAN NOT NULL DEFAULT FALSE,
    apk_download_url          VARCHAR(512) NOT NULL,
    sha256_checksum           VARCHAR(64) NOT NULL,
    apk_size_bytes            BIGINT NOT NULL,
    signing_cert_sha256       VARCHAR(64) NOT NULL,
    release_notes_json        TEXT NOT NULL,
    published_at              TIMESTAMPTZ NOT NULL
);
