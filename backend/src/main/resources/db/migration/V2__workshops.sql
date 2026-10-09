-- Workshops and the three centre locations. Constraint names are part of the
-- API contract: ProblemDetailsAdvice maps uq_workshop_code and
-- ck_workshop_seats_within_capacity to stable error codes.

-- Shared by every table whose rows are permanent history. The message is
-- passed as the trigger argument so each table explains itself.
CREATE FUNCTION forbid_delete() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '%', TG_ARGV[0] USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TABLE location (
    id      uuid        NOT NULL,
    name    varchar(80) NOT NULL,
    active  boolean     NOT NULL DEFAULT true,
    CONSTRAINT pk_location PRIMARY KEY (id),
    CONSTRAINT uq_location_name UNIQUE (name)
);

-- Fixed ids so demo data, tests and any later seed can refer to them.
INSERT INTO location (id, name, active) VALUES
    ('3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b01', 'Northside Studio', true),
    ('3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b02', 'Riverside Hall', true),
    ('3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b03', 'Central Library Annex', true);

CREATE TABLE workshop (
    id                   uuid         NOT NULL,
    -- Stored trimmed and upper-cased by the API; the CHECK keeps it that way,
    -- so a plain UNIQUE is already case-insensitive.
    code                 varchar(32)  NOT NULL,
    title                varchar(120) NOT NULL,
    description          text         NULL,
    instructor           varchar(120) NOT NULL,
    location_id          uuid         NOT NULL,
    starts_at            timestamptz  NOT NULL,
    ends_at              timestamptz  NOT NULL,
    capacity             integer      NOT NULL,
    -- Only ever changed by the two atomic statements behind SeatInventory.
    seats_taken          integer      NOT NULL DEFAULT 0,
    lifecycle            varchar(16)  NOT NULL DEFAULT 'SCHEDULED',
    cancelled_at         timestamptz  NULL,
    cancelled_by         uuid         NULL,
    cancellation_reason  varchar(500) NULL,
    created_at           timestamptz  NOT NULL,
    created_by           uuid         NOT NULL,
    updated_at           timestamptz  NOT NULL,
    updated_by           uuid         NOT NULL,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_workshop PRIMARY KEY (id),
    CONSTRAINT uq_workshop_code UNIQUE (code),
    CONSTRAINT ck_workshop_code_format CHECK (code ~ '^[A-Z0-9-]{3,32}$'),
    CONSTRAINT ck_workshop_capacity CHECK (capacity BETWEEN 1 AND 500),
    -- The hard backstop of the capacity rule (BR-1) and of capacity edits (BR-4).
    CONSTRAINT ck_workshop_seats_within_capacity CHECK (seats_taken BETWEEN 0 AND capacity),
    CONSTRAINT ck_workshop_time_order CHECK (ends_at > starts_at),
    CONSTRAINT ck_workshop_lifecycle CHECK (lifecycle IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_workshop_cancel_consistency CHECK ((lifecycle = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT fk_workshop_location FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT fk_workshop_created_by FOREIGN KEY (created_by) REFERENCES staff_account (id),
    CONSTRAINT fk_workshop_updated_by FOREIGN KEY (updated_by) REFERENCES staff_account (id),
    CONSTRAINT fk_workshop_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES staff_account (id)
);

CREATE INDEX ix_workshop_starts_at ON workshop (starts_at);
CREATE INDEX ix_workshop_lifecycle_starts_at ON workshop (lifecycle, starts_at);

-- FR-WS-09: workshops are cancelled, never deleted.
CREATE TRIGGER trg_workshop_no_delete
    BEFORE DELETE ON workshop
    FOR EACH ROW EXECUTE FUNCTION forbid_delete('workshops are permanent history and can''t be deleted');
