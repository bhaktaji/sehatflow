-- Clinic Queue MVP schema (PostgreSQL).
-- See docs/LLD.md section 2 for the authoritative data model.

CREATE TABLE clinics (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_clinics_slug UNIQUE (slug)
);

CREATE TABLE staff_users (
    id            UUID PRIMARY KEY,
    clinic_id     UUID NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    username      VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(24)  NOT NULL,
    doctor_id     UUID NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_staff_role CHECK (role IN ('OWNER', 'RECEPTIONIST', 'DOCTOR')),
    CONSTRAINT fk_staff_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id),
    CONSTRAINT uq_staff_clinic_username UNIQUE (clinic_id, username)
);
CREATE INDEX ix_staff_clinic_active ON staff_users (clinic_id, active);

CREATE TABLE doctors (
    id           UUID PRIMARY KEY,
    clinic_id    UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    specialty    VARCHAR(120) NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_doctors_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id),
    -- Supports composite tenant-safe foreign keys from queue sessions.
    CONSTRAINT uq_doctors_clinic_id UNIQUE (clinic_id, id)
);

CREATE TABLE queue_sessions (
    id                      UUID PRIMARY KEY,
    clinic_id               UUID NOT NULL,
    doctor_id               UUID NOT NULL,
    business_date           DATE NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    token_prefix            VARCHAR(8)  NOT NULL DEFAULT 'A',
    next_token_number       INTEGER     NOT NULL DEFAULT 1,
    fallback_consult_minutes INTEGER     NOT NULL DEFAULT 10,
    pause_message           VARCHAR(160) NULL,
    opened_at               TIMESTAMPTZ NOT NULL,
    closed_at               TIMESTAMPTZ NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    display_secret_hash     VARCHAR(128) NOT NULL,
    CONSTRAINT ck_session_status CHECK (status IN ('OPEN', 'PAUSED', 'CLOSED')),
    CONSTRAINT ck_session_next_token CHECK (next_token_number > 0),
    CONSTRAINT ck_session_fallback CHECK (fallback_consult_minutes > 0),
    -- Composite tenant-safe FK: (clinic_id, doctor_id) -> doctors(clinic_id, id)
    CONSTRAINT fk_session_doctor FOREIGN KEY (clinic_id, doctor_id)
        REFERENCES doctors (clinic_id, id),
    -- Supports composite tenant-safe foreign keys from queue tokens.
    CONSTRAINT uq_session_clinic_id UNIQUE (clinic_id, id),
    CONSTRAINT uq_session_clinic_doctor_date UNIQUE (clinic_id, doctor_id, business_date)
);
CREATE INDEX ix_sessions_clinic_status_date ON queue_sessions (clinic_id, status, business_date);

CREATE TABLE queue_tokens (
    id                     UUID PRIMARY KEY,
    clinic_id              UUID NOT NULL,
    queue_session_id       UUID NOT NULL,
    token_number           INTEGER NOT NULL,
    public_secret_hash     VARCHAR(128) NOT NULL,
    display_name           VARCHAR(120) NULL,
    status                 VARCHAR(24) NOT NULL DEFAULT 'WAITING',
    priority               BOOLEAN     NOT NULL DEFAULT FALSE,
    queue_position         INTEGER     NOT NULL,
    priority_reason        VARCHAR(240) NULL,
    checked_in_at          TIMESTAMPTZ NOT NULL,
    called_at              TIMESTAMPTZ NULL,
    consultation_started_at TIMESTAMPTZ NULL,
    completed_at           TIMESTAMPTZ NULL,
    version                BIGINT      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_token_status CHECK (status IN
        ('WAITING', 'CALLED', 'IN_CONSULTATION', 'COMPLETED', 'SKIPPED', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT ck_token_number CHECK (token_number > 0),
    CONSTRAINT ck_token_position CHECK (queue_position > 0),
    CONSTRAINT fk_token_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id),
    -- Composite tenant-safe FK: (clinic_id, queue_session_id) -> queue_sessions(clinic_id, id)
    CONSTRAINT fk_token_session FOREIGN KEY (clinic_id, queue_session_id)
        REFERENCES queue_sessions (clinic_id, id),
    CONSTRAINT uq_token_session_number UNIQUE (queue_session_id, token_number),
    CONSTRAINT uq_token_secret UNIQUE (public_secret_hash)
);
CREATE INDEX ix_tokens_clinic_session_status_pos ON queue_tokens (clinic_id, queue_session_id, status, queue_position);
CREATE INDEX ix_tokens_session_status_checkin ON queue_tokens (queue_session_id, status, checked_in_at);

CREATE TABLE queue_audit_events (
    id               UUID PRIMARY KEY,
    clinic_id        UUID NOT NULL,
    queue_session_id UUID NOT NULL,
    token_id         UUID NULL,
    actor_user_id    UUID NULL,
    action           VARCHAR(40) NOT NULL,
    reason           VARCHAR(240) NULL,
    before_state     TEXT NULL,
    after_state      TEXT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_clinic FOREIGN KEY (clinic_id) REFERENCES clinics (id),
    CONSTRAINT fk_audit_session FOREIGN KEY (queue_session_id) REFERENCES queue_sessions (id),
    CONSTRAINT fk_audit_token FOREIGN KEY (token_id) REFERENCES queue_tokens (id)
);
CREATE INDEX ix_audit_session_created ON queue_audit_events (clinic_id, queue_session_id, created_at DESC);
CREATE INDEX ix_audit_token_created ON queue_audit_events (token_id, created_at DESC);
