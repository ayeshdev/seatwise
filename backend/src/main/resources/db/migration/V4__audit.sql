-- The audit trail (FR-AUD). One row per business change, written by the audit
-- module's synchronous listener inside the change's own transaction, so a
-- rolled-back change leaves no row and a committed one is never missing.
CREATE TABLE audit_event (
    id           bigserial    NOT NULL,
    occurred_at  timestamptz  NOT NULL,
    -- NULL when the system acted (first-Admin bootstrap, demo seeding).
    actor_id     uuid         NULL,
    entity_type  varchar(32)  NOT NULL,
    -- No foreign key: it points at a staff account, workshop or registration
    -- depending on entity_type, and none of those rows is ever deleted.
    entity_id    uuid         NOT NULL,
    -- Set for WORKSHOP and REGISTRATION events, so one workshop's timeline
    -- includes its bookings.
    workshop_id  uuid         NULL,
    action       varchar(32)  NOT NULL,
    summary      varchar(300) NOT NULL,
    -- field -> {"from": ..., "to": ...}; never holds a password.
    changes      jsonb        NOT NULL DEFAULT '{}',
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT ck_audit_event_entity_type CHECK (entity_type IN ('STAFF_ACCOUNT', 'WORKSHOP', 'REGISTRATION')),
    CONSTRAINT fk_audit_event_actor FOREIGN KEY (actor_id) REFERENCES staff_account (id)
);

CREATE INDEX ix_audit_event_entity_type_occurred_at ON audit_event (entity_type, occurred_at DESC);
CREATE INDEX ix_audit_event_entity_id_occurred_at ON audit_event (entity_id, occurred_at DESC);
CREATE INDEX ix_audit_event_workshop_id_occurred_at ON audit_event (workshop_id, occurred_at DESC);

-- Append-only: history that can be edited proves nothing.
CREATE FUNCTION forbid_audit_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit events are immutable' USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_change();
