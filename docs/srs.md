# Seatwise — Software Requirements Specification

| | |
|---|---|
| **Product** | Seatwise: workshop registration service |
| **Client** | Community training centre (3 locations, ~15 staff) |
| **Version** | 1.0 (draft for build) |
| **Date** | 2026-10-09 |
| **Source brief** | Client requirements brief: Workshop Registration Service |
| **Companion docs** | `docs/architecture.md` (how), `plans/implementation-plan.md` (when) |

Requirement keywords: **MUST** = required for acceptance, **SHOULD** =
expected unless time runs out (listed in "skipped" if so), **MAY** = bonus.

---

## 1. Introduction

### 1.1 Purpose

This document defines what Seatwise must do and how well it must do it.
Every requirement is traceable to the client brief (section 9). It is the
reference for implementation, testing and acceptance.

### 1.2 Problem statement

Registrations are taken by phone and in person and written into a shared
spreadsheet. Two failures follow:

1. **Overbooking.** Two staff members promise the last seat at the same
   moment. *("Last Saturday 24 people turned up for a workshop with 20
   seats.")*
2. **Lost history.** When someone cancels, nobody frees the seat or records
   who cancelled it.

### 1.3 Scope

**In scope:** staff accounts with three roles, the workshop catalogue,
attendee registration and cancellation with full history, finding
workshops, an audit trail (bonus), a waitlist (bonus), local setup with seed
data, and a live deployment.

**Out of scope:** public/attendee self-service, payments, email or SMS
notifications to attendees, instructor accounts, recurring-workshop series,
reporting/exports, multi-tenant use, and mobile apps.

### 1.4 Definitions

| Term | Meaning |
|---|---|
| **Staff member** | A person with a Seatwise login: Admin, Manager or Staff. |
| **Admin** | Looks after staff accounts and roles. |
| **Manager** | The programme manager. Schedules and edits workshops, and also books attendees. |
| **Staff** | Front-desk staff. Register and cancel attendees. |
| **Attendee** | A member of the public, recorded as a name plus an email. They have no account. |
| **Workshop** | A single scheduled session with a fixed number of seats. |
| **Registration** | A record linking an attendee to a workshop. Status ACTIVE, WAITLISTED or CANCELLED. |
| **Active registration** | A registration holding a seat (status ACTIVE). |
| **Seats taken** | The number of active registrations for a workshop. |
| **Waitlist** | The ordered WAITLISTED registrations for a full workshop. |
| **Audit event** | An immutable record of who changed what, and when. |

---

## 2. Overall description

### 2.1 Product perspective

Seatwise is a new, self-contained web application that replaces the shared
spreadsheet. It has a browser front end (Seatwise Desk), a REST API, a
PostgreSQL database and a Keycloak identity provider. See
`docs/architecture.md` §3–4.

### 2.2 User classes

| Class | Count | Technical skill | Main goal |
|---|---|---|---|
| Admin | 1–2 | Low–medium | Create accounts, set roles, remove access |
| Manager | 1–3 | Low | Keep the workshop schedule correct |
| Staff | ~10–12 | Low; on the phone while using it | Book or cancel someone in seconds, without double-booking |

The team is non-technical and wants **no training required**. The UI must
explain itself (NFR-USE).

### 2.3 Operating environment

- A current evergreen desktop browser (Chrome, Edge, Firefox, Safari: latest
  two versions), at screen widths from 1280 px down to tablet (768 px).
- The server runs as Docker containers: Docker Compose locally and Dokploy in
  production.

### 2.4 Constraints

- **C-1.** Stack: Java 25 / Spring Boot 4 / Spring Modulith / PostgreSQL 17 /
  Keycloak 26 / Angular 20 / Docker (the team's existing stack).
- **C-2.** Build budget: 3 hours for the core, prioritised in this order:
  access control → capacity rule and history → working frontend → search →
  everything else.
- **C-3.** One repository holds both backend and frontend, hosted on GitHub.
- **C-4.** No public sign-up. The first Admin is seeded.
- **C-5.** Delivery runs through GitHub Actions and Dokploy.

### 2.5 Assumptions

| ID | Assumption | Impact if wrong |
|---|---|---|
| A-1 | The permission matrix is literal. An Admin can **not** view workshops or registrations. | One policy change plus its test row. |
| A-2 | Each staff member has exactly one role. | The role becomes a set, and the UI needs a role picker. |
| A-3 | All three locations share one timezone, set in configuration. | Store a timezone per location. |
| A-4 | Each workshop is a single session. Multi-session courses are not needed. | A new "series" concept. |
| A-5 | An attendee is identified per workshop by email (case-insensitive). The same email can't hold two live registrations for the same workshop. | Allow duplicates (e.g. a parent booking two children under one email). Confirm with the client. |
| A-6 | Cancelling a workshop keeps its registrations as they are and blocks new ones. Staff contact attendees outside the system. | A bulk-cancel action. |
| A-7 | The waitlist auto-promotes the next person, and staff phone them to confirm. | An explicit "offer → accept/decline" step with a hold timer. |
| A-8 | Registration closes when a workshop starts. | A configurable cut-off. |
| A-9 | Staff accounts are deactivated, never deleted, because history references them. | n/a |
| A-10 | Each attendee email is typed by staff and isn't verified. | n/a |

---

## 3. Functional requirements

### 3.1 Authentication and staff accounts (FR-AUTH)

| ID | Requirement | Priority |
|---|---|---|
| FR-AUTH-01 | Staff MUST sign in with email and password. There MUST be no self-registration. | MUST |
| FR-AUTH-02 | On first start, the system MUST create the first Admin from configured credentials if no active Admin exists. This MUST be idempotent. | MUST |
| FR-AUTH-03 | An Admin MUST be able to create a staff account with full name, email, role (Admin/Manager/Staff) and a temporary password. | MUST |
| FR-AUTH-04 | A newly created user MUST be required to change the temporary password at first sign-in (production). | SHOULD |
| FR-AUTH-05 | An Admin MUST be able to change a staff member's role. The change MUST apply on that user's next request, without waiting for re-login. | MUST |
| FR-AUTH-06 | An Admin MUST be able to deactivate and reactivate an account. A deactivated user MUST be refused on their next request and MUST NOT be able to sign in. | MUST |
| FR-AUTH-07 | An Admin MUST NOT be able to change their own role or deactivate themselves. The last active Admin MUST NOT be demoted or deactivated. | MUST |
| FR-AUTH-08 | Email addresses MUST be unique across staff accounts (case-insensitive). | MUST |
| FR-AUTH-09 | An Admin SHOULD be able to set a new temporary password for a staff member. | SHOULD |
| FR-AUTH-10 | Any signed-in user MUST be able to see their own name and role, and sign out. | MUST |

### 3.2 Access control (FR-ACL)

| ID | Requirement | Priority |
|---|---|---|
| FR-ACL-01 | The backend MUST enforce this matrix on every request:<br>• Create user accounts and set roles: **Admin** only<br>• Add and edit workshops: **Manager** only<br>• Register and cancel attendees: **Manager, Staff**<br>• View workshops, registrations and history: **Manager, Staff** | MUST |
| FR-ACL-02 | Any action not allowed by FR-ACL-01 MUST be refused by the API with HTTP 403 (401 if unauthenticated), whatever the UI shows. | MUST |
| FR-ACL-03 | The UI SHOULD hide navigation and actions the user's role can't perform. | SHOULD |
| FR-ACL-04 | An automated test MUST cover every endpoint × every role. | MUST |

### 3.3 Workshop catalogue (FR-WS)

| ID | Requirement | Priority |
|---|---|---|
| FR-WS-01 | A workshop MUST record: **code** (unique, short, upper-case), **title**, **instructor**, **start date and time**, **capacity** (number of seats) and **status**. | MUST |
| FR-WS-02 | A workshop SHOULD also record: **location** (one of the three centres), **end date and time**, an optional **description**, and who created or last edited it and when. | SHOULD |
| FR-WS-03 | A Manager MUST be able to create a workshop. Validation: code unique; title and instructor required; start in the future; end after start; capacity a whole number from 1 to 500. | MUST |
| FR-WS-04 | A Manager MUST be able to edit a workshop. Capacity MUST NOT be reduced below the seats already taken; the error MUST say how many are taken. | MUST |
| FR-WS-05 | When two Managers edit the same workshop concurrently, the second save MUST be rejected with a "someone else changed this" message instead of silently overwriting. | MUST |
| FR-WS-06 | A Manager MUST be able to cancel a workshop. A cancelled workshop accepts no new registrations, and its existing registrations stay visible. | MUST |
| FR-WS-07 | The status shown MUST always be current, derived as OPEN, FULL, IN_PROGRESS, COMPLETED or CANCELLED (see architecture §6). | MUST |
| FR-WS-08 | Each workshop MUST show capacity, seats taken, seats left and the waitlist count. | MUST |
| FR-WS-09 | Workshops are never hard-deleted. | MUST |

### 3.4 Registrations (FR-REG)

| ID | Requirement | Priority |
|---|---|---|
| FR-REG-01 | Manager or Staff MUST be able to register an attendee for an OPEN workshop by entering the attendee's name and email. | MUST |
| FR-REG-02 | **Capacity rule:** a workshop MUST NEVER hold more active registrations than its capacity, including when many registrations for the same workshop arrive at the same moment from different staff. | MUST |
| FR-REG-03 | If the workshop is full when the request is processed, the request MUST be refused with a clear message. Nothing may be partially saved. | MUST |
| FR-REG-04 | The same email MUST NOT hold more than one active or waitlisted registration for the same workshop. A double click or a retried request MUST NOT take two seats. | MUST |
| FR-REG-05 | Registration MUST be refused for workshops that are cancelled, in progress or completed. | MUST |
| FR-REG-06 | Manager or Staff MUST be able to cancel a registration, with an optional reason. Cancelling frees the seat immediately. | MUST |
| FR-REG-07 | A cancelled registration MUST NEVER be deleted. It MUST keep who cancelled it, when, and the reason. | MUST |
| FR-REG-08 | Every registration MUST record who registered it and when. | MUST |
| FR-REG-09 | Manager or Staff MUST be able to view the complete registration history of a workshop, including cancelled and waitlisted entries, with who and when for each action. Filtering by status is SHOULD. | MUST |
| FR-REG-10 | If two staff cancel the same registration at once, exactly one cancellation MUST be recorded. The other MUST be told it was already cancelled. | MUST |

### 3.5 Finding workshops (FR-FIND)

| ID | Requirement | Priority |
|---|---|---|
| FR-FIND-01 | Staff MUST be able to filter workshops by **date range**. | MUST |
| FR-FIND-02 | Staff MUST be able to filter by **status** (one or more). | MUST |
| FR-FIND-03 | Staff MUST be able to filter to **workshops with seats still available**. | MUST |
| FR-FIND-04 | One click SHOULD answer "which workshops this week still have seats". This SHOULD be the default view for Manager and Staff. | SHOULD |
| FR-FIND-05 | Staff SHOULD be able to filter by location and search by code, title or instructor. | SHOULD |
| FR-FIND-06 | Results MUST be paged and sorted by start time (soonest first) by default. | MUST |
| FR-FIND-07 | Filters SHOULD be reflected in the URL so a view can be bookmarked or refreshed. | SHOULD |

### 3.6 Audit trail (FR-AUD), bonus

| ID | Requirement | Priority |
|---|---|---|
| FR-AUD-01 | The system MAY record an audit event for every workshop create, edit and cancel, with changed fields (from → to), actor and time. | MAY |
| FR-AUD-02 | The system MAY record an audit event for every account create, role change, deactivation, reactivation and password reset. | MAY |
| FR-AUD-03 | Audit events MUST be immutable once written, if implemented. | MAY |
| FR-AUD-04 | An Admin MAY view account audit events. Manager and Staff MAY view workshop and registration audit events. | MAY |
| FR-AUD-05 | An audit event MUST be written in the same transaction as the change it describes, so neither exists without the other. | MAY |

### 3.7 Waitlist (FR-WL), bonus

| ID | Requirement | Priority |
|---|---|---|
| FR-WL-01 | When a workshop is full, staff MAY add the attendee to its waitlist instead. | MAY |
| FR-WL-02 | Waitlist order MUST be first come, first served (by time added). | MAY |
| FR-WL-03 | When an active registration is cancelled, the seat MUST go to the first person on the waitlist in the same transaction. Nobody else can take the seat in between. | MAY |
| FR-WL-04 | Promoted attendees MUST be visibly flagged ("promoted from waitlist — call to confirm"). | MAY |
| FR-WL-05 | Staff MAY remove someone from the waitlist (recorded as a cancellation). | MAY |
| FR-WL-06 | Promotions MUST respect the capacity rule and the history rules (FR-REG-02, FR-REG-07). | MAY |

### 3.8 Setup and demo data (FR-OPS)

| ID | Requirement | Priority |
|---|---|---|
| FR-OPS-01 | A reviewer MUST be able to run the whole system locally with one command (`docker compose up`) after copying `.env.example`. | MUST |
| FR-OPS-02 | Local and demo environments MUST seed: one Admin (credentials in README), one demo Manager, one demo Staff, the three locations, and at least 8 sample workshops covering open, nearly full, full, cancelled and past cases. | MUST |
| FR-OPS-03 | README MUST describe local setup for both full-Docker and dev-loop modes, plus the demo logins. | MUST |
| FR-OPS-04 | A one-page design note MUST cover stack choices and why, design decisions, trade-offs, assumptions, how over-registration is prevented, and what was skipped. | MUST |
| FR-OPS-05 | The system SHOULD be deployed live via GitHub Actions → Dokploy. | SHOULD |

---

## 4. Business rules

| ID | Rule |
|---|---|
| BR-1 | `active registrations(workshop) ≤ capacity(workshop)`, always. This is enforced by the database, not only by code. |
| BR-2 | Registrations and audit events are append-only history. Status changes; rows are never deleted. |
| BR-3 | Only SCHEDULED workshops that have not started accept registrations. |
| BR-4 | Capacity may only be edited to a value ≥ seats taken. |
| BR-5 | One live (active or waitlisted) registration per email per workshop. |
| BR-6 | There is always at least one active Admin. |
| BR-7 | A role or active-flag change takes effect on the user's next request. |

---

## 5. External interface requirements

### 5.1 User interface

- **UI-1.** A role-aware navigation: Admin sees *Staff accounts* and
  *Account activity*. Manager and Staff see *Workshops* and *Activity*.
- **UI-2.** The workshop list has date presets (Today / This week / Next 7
  days / Custom), a status multi-select, a location select, a "Has seats"
  toggle and a search box. Each row shows a seat meter.
- **UI-3.** The workshop detail page shows the details, the seat meter, a
  register form, the registration table (all statuses, with who/when) and
  an activity tab.
- **UI-4.** All errors are shown in plain language with a suggested next
  step. No codes or stack traces.
- **UI-5.** Every destructive action has a confirmation step.

### 5.2 API

REST/JSON under `/api/v1`, documented by OpenAPI. Errors use RFC 9457
problem details with a stable `code`. The endpoint list and error catalogue
are in `docs/architecture.md` §9.

### 5.3 Identity provider

OIDC (Authorization Code + PKCE) against Keycloak realm `seatwise`. The API
manages staff users through Keycloak's Admin REST API, using a
least-privilege service account.

---

## 6. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-SEC-01 | Security | All API endpoints except health and OpenAPI require a valid token from the `seatwise` realm. |
| NFR-SEC-02 | Security | Passwords are handled only by Keycloak. The application never sees or stores them, apart from relaying an Admin-typed temporary password. |
| NFR-SEC-03 | Security | Secrets come only from the environment. None are committed (except dev-only demo credentials, clearly labelled). |
| NFR-SEC-04 | Security | Brute-force protection and a password policy (≥ 10 characters) are enabled on the realm. |
| NFR-SEC-05 | Security | All input is validated server-side. Size limits are applied. |
| NFR-CON-01 | Concurrency | 50 simultaneous registration requests for a 20-seat workshop yield exactly 20 active registrations. This is an automated test run in CI. |
| NFR-CON-02 | Concurrency | Registrations for different workshops do not block each other. |
| NFR-PERF-01 | Performance | p95 API latency < 300 ms for search and registration at 20 concurrent users on a 2 vCPU server. |
| NFR-PERF-02 | Performance | The workshop list loads in < 2 s on a typical office connection. |
| NFR-USE-01 | Usability | A new staff member can register an attendee unaided on first use (verified by walkthrough). |
| NFR-USE-02 | Usability | Registering an attendee takes ≤ 3 interactions from the workshop list. |
| NFR-USE-03 | Accessibility | WCAG 2.1 AA basics: labels, contrast, keyboard operation, focus visibility. |
| NFR-REL-01 | Reliability | No acknowledged registration or cancellation is lost. Each is committed before the API responds. |
| NFR-REL-02 | Reliability | Database migrations are versioned (Flyway), forward-only, and run automatically on start. |
| NFR-REL-03 | Reliability | Health endpoints are exposed for container orchestration. |
| NFR-MNT-01 | Maintainability | Backend module boundaries are verified in CI (Spring Modulith). |
| NFR-MNT-02 | Maintainability | CI runs backend tests (including the concurrency and access-matrix tests), plus frontend lint, unit tests and build, on every pull request. |
| NFR-OBS-01 | Observability | Structured logs include the actor id and request id. `/actuator/info` reports the deployed git SHA. |
| NFR-DEP-01 | Deployability | Merging to `main` builds immutable images tagged by SHA and deploys them to Dokploy without manual steps. Rollback means redeploying a previous tag. |
| NFR-TIME-01 | Time | All timestamps are stored in UTC and shown in the centre's timezone. |

---

## 7. Data requirements

The entities are staff_account, location, workshop, registration and
audit_event. Fields, constraints and indexes are in `docs/architecture.md`
§6. Retention: indefinite for all history; no purge is in scope. Personal
data is limited to attendee name and email and staff name and email.

---

## 8. Acceptance criteria (demo script)

1. `cp .env.example .env && docker compose up -d --build`, then open
   http://localhost:4280.
2. Sign in as **Admin**. You see Staff accounts only. Create a Staff user.
   The Workshops page is not in the nav, and calling `/api/v1/workshops`
   with the Admin token returns 403.
3. Sign in as **Manager**. Create a workshop with capacity 2. Edit its
   title. The activity tab shows the change.
4. Sign in as **Staff** in two browsers. Book seat 1. Then press Register in
   both browsers at once for seat 2: one succeeds, and the other sees "the
   last seat was just taken". The Staff user can't see Edit on the workshop,
   and `PUT /workshops/{id}` with the Staff token returns 403.
5. Cancel one registration. Its seat is freed, the row remains with
   "cancelled by … at …", and (if the waitlist is built) the waitlisted
   person is promoted.
6. As Manager, try to set capacity below the seats taken. It's refused with
   the count.
7. On the workshop list, "This week · has seats" shows only matching
   workshops.
8. CI is green on the pull request. A merge to `main` deploys, and
   `/actuator/info` on the live site shows the merged SHA.

---

## 9. Traceability to the brief

| Brief section | Requirements |
|---|---|
| 1. Staff access and permissions | FR-AUTH-01…10, FR-ACL-01…04, BR-6, BR-7 |
| 2. Workshop catalogue (+ "anything else worth tracking") | FR-WS-01…09 (location, end time, description, audit fields added) |
| 3. Registrations: capacity rule, history, concurrency | FR-REG-01…10, BR-1…5, NFR-CON-01/02 |
| 4. Finding workshops | FR-FIND-01…07 |
| Bonus: audit trail | FR-AUD-01…05 |
| Bonus: waitlist | FR-WL-01…06 |
| Deliverables: repo, setup, seeded admin, sample workshops, one-page doc, live deploy | FR-OPS-01…05, NFR-DEP-01 |
| "What we look for": API design, frontend structure, access control, capacity under concurrency | Architecture §7–10, FR-ACL-04, NFR-CON-01 |

---

## 10. Open questions for the client

1. Should Admins also be able to view the catalogue (assumption A-1)?
2. Can one email book several seats for the same workshop, e.g. a parent and
   children (A-5)?
3. When a workshop is cancelled, should all its registrations be cancelled
   automatically (A-6)?
4. Should waitlisted people be offered the seat with a deadline, rather than
   auto-promoted (A-7)?
5. Do the three locations share a timezone (A-3)?
