-- Registrations are append-only history (BR-2): a cancellation is a status
-- change with who/when/why, and the row stays forever.
CREATE TABLE registration (
    id                   uuid         NOT NULL,
    workshop_id          uuid         NOT NULL,
    attendee_name        varchar(120) NOT NULL,
    attendee_email       varchar(254) NOT NULL,
    status               varchar(16)  NOT NULL,
    registered_at        timestamptz  NOT NULL,
    registered_by        uuid         NOT NULL,
    promoted_at          timestamptz  NULL,
    cancelled_at         timestamptz  NULL,
    cancelled_by         uuid         NULL,
    cancellation_reason  varchar(500) NULL,
    CONSTRAINT pk_registration PRIMARY KEY (id),
    CONSTRAINT ck_registration_status CHECK (status IN ('ACTIVE', 'WAITLISTED', 'CANCELLED')),
    CONSTRAINT ck_registration_cancel_consistency CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT fk_registration_workshop FOREIGN KEY (workshop_id) REFERENCES workshop (id),
    CONSTRAINT fk_registration_registered_by FOREIGN KEY (registered_by) REFERENCES staff_account (id),
    CONSTRAINT fk_registration_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES staff_account (id)
);

-- BR-5 / FR-REG-04: one live booking per attendee per workshop. Also what stops
-- a double click or a retried request from taking two seats. The API maps a
-- violation of this exact name to 409 DUPLICATE_REGISTRATION.
CREATE UNIQUE INDEX uq_registration_live_attendee
    ON registration (workshop_id, lower(attendee_email))
    WHERE status IN ('ACTIVE', 'WAITLISTED');

CREATE INDEX ix_registration_workshop_status ON registration (workshop_id, status);
-- History (newest first) and the waitlist queue (oldest first).
CREATE INDEX ix_registration_workshop_registered_at ON registration (workshop_id, registered_at);

CREATE TRIGGER trg_registration_no_delete
    BEFORE DELETE ON registration
    FOR EACH ROW EXECUTE FUNCTION forbid_delete('registrations are permanent history and can''t be deleted');
