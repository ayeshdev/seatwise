# Decisions

Append-only log of architectural decisions (ADR-lite). Newest last.

## 2026-10-09 — Reuse the team's production stack

**Decision:** Java 25 / Spring Boot 4 / Spring Modulith / PostgreSQL 17 /
Keycloak 26 / Angular 20 / Docker. Delivery via GitHub Actions → GHCR → Dokploy.
**Why:** the time goes into the domain, not tooling. Postgres gives the
locking and constraints the capacity rule needs.
**Trade-off:** Keycloak is heavier than a JWT-only API for a 3-hour build.
The fallback is in `plans/implementation-plan.md` → Risk register.

## 2026-10-09 — Capacity via atomic conditional UPDATE + CHECK constraint

**Decision:** `UPDATE workshop SET seats_taken = seats_taken + 1 WHERE id = ? AND seats_taken < capacity AND …`
inside the registration transaction, backed by `CHECK (seats_taken BETWEEN 0 AND capacity)`.
**Why:** one statement, no read-check-write window, and no contention across
different workshops. The DB guarantees the invariant even against future
code bugs.
**Alternatives:** `SELECT … FOR UPDATE` + count (fallback), SERIALIZABLE +
retry, optimistic locking, app-level locks. See `docs/architecture.md` §7.

## 2026-10-09 — Keycloak authenticates, the app DB authorizes

**Decision:** roles and the active flag live in `staff_account`. The API maps
JWT `sub` → role on each request.
**Why:** role changes and deactivation take effect immediately, not when the
token expires.

## 2026-10-09 — Literal permission matrix: Admin cannot view workshops

**Decision:** follow the client's matrix exactly (Admin → 403 on catalogue
endpoints).
**Why:** "Anything not marked must be refused by the backend." Open question
1 in the SRS asks the client to confirm.

## 2026-10-09 — Synchronous audit in the same transaction

**Decision:** a plain `@EventListener` writes `audit_event` inside the
publisher's transaction.
**Why:** an audit row can never be missing for a committed change, or
present for a rolled-back one.

## 2026-10-09 — Meilisearch for workshop search

**Decision:** `GET /api/v1/workshops` is served from a Meilisearch index
`workshops` (new `search` module). PostgreSQL stays the system of record.
**Why:** instant, typo-tolerant search with filters for a front desk on the
phone. This was the user's choice.
**Guard rails:** after-commit incremental sync, plus rebuild on start and
nightly. Seat counts are re-read from Postgres for each result page. A
Postgres fallback runs when the index is down. Bookings never read the
index. See `docs/architecture.md` §7a.
**Trade-off:** a sixth production service and millisecond index lag, in
exchange for better search UX.

## 2026-10-09 — Warm, minimal visual language for Seatwise Desk

**Decision:** an original token-based theme. Cream canvas, terracotta
accent, serif headings over a sans body, hairline borders, generous
whitespace, and light and dark modes (`docs/architecture.md` §10).
**Why:** a calm, legible UI suits non-technical staff under time pressure.
This was the user's choice.
**Constraint:** no third-party logos, names or brand assets. Fonts are
open-licence (`Source Serif 4`, `Inter`) and self-hosted.

## 2026-10-09 — Demo accounts on the live demo deployment

**Decision:** the live deployment runs with the `demo` profile, so reviewers can
sign in as the demo Manager and Staff straight away. It uses the same dev-only
passwords that are published in the README.
**Why:** the brief asks for a seeded login and sample workshops so the reviewers
"can try it straight away". There is no real data on that instance.
**Constraint:** a real production deployment drops `demo` from
`SPRING_PROFILES_ACTIVE`. The `prod` profile refuses to start with any known dev
default for the provisioner secret, the bootstrap Admin password or the search key.
