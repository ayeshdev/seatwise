-- Staff accounts. The id is the Keycloak user id (JWT `sub`), so the API can
-- authorize a request with one primary-key lookup. Keycloak only authenticates;
-- role and active flag live here so changes apply on the very next request.
CREATE TABLE staff_account (
    id          uuid         NOT NULL,
    email       varchar(254) NOT NULL,
    full_name   varchar(120) NOT NULL,
    role        varchar(16)  NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    -- NULL only for rows seeded by the system (first Admin, demo users).
    created_by  uuid         NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  uuid         NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_staff_account PRIMARY KEY (id),
    CONSTRAINT ck_staff_account_role CHECK (role IN ('ADMIN', 'MANAGER', 'STAFF')),
    CONSTRAINT fk_staff_account_created_by FOREIGN KEY (created_by) REFERENCES staff_account (id),
    CONSTRAINT fk_staff_account_updated_by FOREIGN KEY (updated_by) REFERENCES staff_account (id)
);

-- Case-insensitive uniqueness (FR-AUTH-08). The API maps a violation of this
-- exact name to 409 EMAIL_IN_USE, so the name is part of the contract.
CREATE UNIQUE INDEX uq_staff_account_email ON staff_account (lower(email));
