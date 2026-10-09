# Seatwise — Implementation Plan

Build order for Seatwise. It follows the brief's priority list: access control
→ capacity rule and history → end-to-end frontend → search → everything else.
Each phase ends in a **working, committed, CI-green** state, so we can stop
anywhere and still hand in something that works. "A clean, connected, working
application will always score higher than a feature-heavy but broken one."

References: `docs/srs.md` (requirement IDs), `docs/architecture.md` (§
numbers), `plans/cicd-dokploy.md` (delivery).

---

## 0. Time budget (3-hour core + bonus)

| Phase | Scope | Box | Cumulative |
|---|---|---|---|
| P0 | Repo, skeletons, Compose (incl. Meilisearch), Keycloak realm, CI skeleton | 25 min | 0:25 |
| P1 | Schema + access control (accounts, `/me`, policies, matrix test) | 35 min | 1:00 |
| P2 | Workshops + **capacity rule** + registrations + history + concurrency test | 50 min | 1:50 |
| P3 | Frontend end to end on the warm/minimal design tokens (login, list, detail, register/cancel, workshop form, staff accounts) | 50 min | 2:40 |
| P4 | Finding workshops with Meilisearch (index, sync, Postgres fallback, filters, presets, URL state) | 30 min | 3:10 |
| P5 | README, seed data check, one-page design note, list of what was skipped | 5 min + during | 3:15 |
| B1 | Bonus: audit trail (backend + activity UI) | 25 min | — |
| B2 | Bonus: waitlist | 30 min | — |
| D | Deliver: `deliver.yml`, Dokploy wiring, live smoke | 30 min | — |

**Rule:** if a phase runs over its box by more than 10 minutes, cut its
SHOULDs and move on. Cuts go to `BACKLOG.md`, and they go into the design
note's "skipped" section.

**Risk register**

| Risk | Mitigation |
|---|---|
| Keycloak setup eats the clock (realm import, PKCE, admin API) | Realm JSON is written once in P0 and verified by logging in before any domain code exists. Fallback if it's still broken at 0:40: keep Keycloak for login, and seed demo users in the realm file so account creation via the admin API becomes a P5+ item. Log the fallback in `DECISIONS.md`. |
| Testcontainers slow or unavailable on the Windows dev box | It always runs in CI (Ubuntu runners have Docker). Locally, it needs Docker Desktop running. |
| Spring Boot 4 / Modulith 2 API drift | Pin the same versions the team already runs (Boot 4.0.5, Modulith 2.0.5). |
| Angular + keycloak-angular version mismatch | Pin keycloak-angular 20.x with keycloak-js 26.x (a known-good pair). |
| Meilisearch sync or filter mismatch eats P4 | The Postgres fallback (`JpaWorkshopSearch`) is written **first** and is a complete implementation. Meilisearch (`MeiliWorkshopSearch`) is layered on top behind the same `WorkshopSearch` interface, so if time runs out the list still works and the index is listed as skipped. |
| Meilisearch client vs Java 25 / Boot 4 | `meilisearch-java` is a plain HTTP client with no Spring coupling. If it misbehaves, call the REST API directly with `RestClient` (only 4 endpoints are used). |
| Clock/timezone bugs in "this week" | One `Clock` bean, one `seatwise.centre-timezone`, and a test for the week boundary. |

---

## P0: Foundations (25 min)

**Goal:** `docker compose up` brings up db + idp + search + api + desk. You can log in
through Keycloak and the API answers `/actuator/health`. CI runs on pull
requests.

| # | Task | Files |
|---|---|---|
| 0.1 | `git init`, `.gitignore`, `.editorconfig`, `.gitattributes`, root `package.json` scripts, `README.md` stub | root |
| 0.2 | Backend skeleton: Gradle wrapper, `build.gradle` (web, validation, data-jpa, security, oauth2-resource-server, actuator, flyway + `spring-boot-flyway`, modulith core/test, springdoc, postgres, `meilisearch-java`, testcontainers-postgresql, awaitility, spring-security-test), `SeatwiseApplication`, `application.yml` + `application-{dev,demo,prod,test}.yml` | `backend/**` |
| 0.3 | Module packages with `package-info.java` `@ApplicationModule` (`accounts`, `workshops`, `registrations`, `audit`, `search`, `common` OPEN) + `ModularityTests` | `backend/src/main/java/com/seatwise/**` |
| 0.4 | Frontend skeleton: `ng new seatwise-desk` (standalone, SCSS → Tailwind 3, no SSR), Jest + Playwright config, `proxy.conf.json` → 8280 | `frontend/**` |
| 0.5 | Keycloak realm `infra/keycloak/seatwise-realm.json`: realm `seatwise`, `registrationAllowed=false`, brute-force on, password policy; client `seatwise-desk` (public, PKCE S256, redirect `http://localhost:4280/*`, `http://localhost:4210/*`), client scope adding audience `seatwise-api`; dev-only client `seatwise-dev-tools` (direct access grants, for `curl` probing; `enabled` driven by a `${SEATWISE_DEV_TOOLS_ENABLED:false}` placeholder, set true only in local `.env`. Verify that Keycloak's import substitutes it); confidential client `seatwise-provisioner` (service account, roles `realm-management: manage-users, view-users, query-users`) | `infra/keycloak/` |
| 0.6 | `compose.yaml` (db, idp, search, api, desk with healthchecks + `depends_on: condition: service_healthy`; `search` = `getmeili/meilisearch:v1.43.0`, `MEILI_MASTER_KEY`, `MEILI_ENV=development`, `MEILI_NO_ANALYTICS=true`, volume `search-data`, healthcheck `GET /health`), `infra/postgres/init/01-databases.sh`, `.env.example` (ports 5442/8281/7710/8280/4280, bootstrap admin, demo passwords, provisioner secret, `SEATWISE_SEARCH_MASTER_KEY`) | root, `infra/` |
| 0.7 | Dockerfiles: `backend/Dockerfile` (gradle:9-jdk25 build → temurin:25-jre runtime, non-root, `HEALTHCHECK`), `frontend/Dockerfile` (node:22 + pnpm build → nginx:1.27-alpine), `frontend/nginx/desk.conf.template` (SPA fallback, `/api` proxy to `${API_UPSTREAM}`, `/config.json` from env via `envsubst` entrypoint) | |
| 0.8 | `.github/workflows/ci.yml` skeleton (backend + frontend jobs). See `plans/cicd-dokploy.md` §3 | `.github/` |
| 0.9 | Root `CONTEXT.md`, `TASKS.md`, `BACKLOG.md`, `DECISIONS.md`, `CHANGELOG.md` | root |

**Done when:** `docker compose up -d --build` → all four containers healthy;
logging in at the Keycloak account console works for the bootstrap admin; CI
is green on the first PR.

---

## P1: Access control (35 min). SRS FR-AUTH, FR-ACL

| # | Task |
|---|---|
| 1.1 | Flyway `V1__staff_accounts.sql`: `staff_account` (id = Keycloak sub, unique `lower(email)`, role CHECK, active, audit cols, version). |
| 1.2 | `common.security`: `SecurityConfig` (stateless, resource server, `/actuator/health/**` + OpenAPI public, everything else authenticated), issuer + audience validator, `Policies` constants, `ActorProvider` (current staff id). `accounts.internal.StaffAuthenticationConverter` (a `Converter<Jwt, AbstractAuthenticationToken>` bean: sub → `staff_account` → `ROLE_x`; missing/inactive → `ACCOUNT_INACTIVE`). It lives in `accounts` so that `common` never depends on a domain module. |
| 1.3 | `common.error`: `ErrorCode` enum, `DomainException`, `ProblemDetailsAdvice` (`@RestControllerAdvice`) with the error-code catalogue (architecture §9), plus mapping of the named DB constraints (`ck_workshop_seats_within_capacity`, `uq_registration_live_attendee`, `uq_workshop_code`, `uq_staff_account_email`) to codes. `common.web.PageResponse<T>`. |
| 1.4 | `accounts`: `StaffDirectory` (public), `internal/StaffAccountEntity`, repository, `StaffAccountService`, `KeycloakProvisioner` (RestClient + client-credentials token; create user, set temporary password, enable/disable, compensating delete), `StaffAccountController` (`GET/POST /staff-accounts`, `GET/PATCH /{id}`, `POST /{id}/password-reset`), `MeController` (`GET /me`). Guards: `LAST_ADMIN`, `SELF_MODIFICATION`, `EMAIL_IN_USE`, `STALE_VERSION`. |
| 1.5 | `AdminBootstrapRunner` (FR-AUTH-02) + `DemoStaffSeeder` (`demo` profile: manager@ / staff@seatwise.local). |
| 1.6 | **`AccessMatrixTest`**: a parameterized `@WebMvcTest` over a table `(method, path, role) → expected`, using `jwt()` plus a mocked `StaffDirectory`. Rows exist for every endpoint planned in P2 as well, so they start out failing on 404 and turn green as endpoints land. |
| 1.7 | Service tests (Testcontainers): create account, role change applies immediately, last-admin guard, deactivate → 403 on the next call. |

**Done when:** an Admin token can create a Staff account through the API, a
Staff token gets 403 on `/staff-accounts`, and `AccessMatrixTest` is green for
the P1 rows.

---

## P2: Workshops, capacity rule, history (50 min). SRS FR-WS, FR-REG, BR-1…5

| # | Task |
|---|---|
| 2.1 | `V2__workshops.sql`: `location` (+ 3 seeded rows), `workshop` (all columns; `CHECK (capacity BETWEEN 1 AND 500)`, `CHECK (seats_taken BETWEEN 0 AND capacity)`, `CHECK (ends_at > starts_at)`, unique `code`, indexes). |
| 2.2 | `V3__registrations.sql`: `registration` (status CHECK, cancel-consistency CHECK, partial unique index `(workshop_id, lower(attendee_email)) WHERE status IN ('ACTIVE','WAITLISTED')`, index `(workshop_id, status)`), `BEFORE DELETE` trigger that blocks deletes. |
| 2.3 | `workshops`: `WorkshopEntity` (`seatsTaken` read-only mapping, `@Version`), `WorkshopCatalogue` (public read views + derived status), `SeatInventory` (public: `tryClaimSeat`, `releaseSeat` — native conditional `UPDATE`s, `@Transactional(propagation = MANDATORY)` so they can only run inside the caller's transaction), `WorkshopService` (create, update with version check, cancel; capacity `CHECK` violation → `CAPACITY_BELOW_TAKEN`), `WorkshopController`, `LocationController`. |
| 2.4 | `registrations`: `RegistrationEntity`, `RegistrationService.register()` (claim seat → insert; unique violation → `DUPLICATE_REGISTRATION`; 0 rows → `WORKSHOP_FULL` / `WORKSHOP_NOT_OPEN`), `cancel()` (conditional status update → `ALREADY_CANCELLED`; release the seat), `history(workshopId, status?)` with registered-by / cancelled-by names via `StaffDirectory`. Controller endpoints per architecture §9. |
| 2.5 | Publish domain events (records in each module root) from services. Listeners arrive in B1. |
| 2.6 | **`CapacityConcurrencyIT`**: 50 threads, `CountDownLatch` start gate, 20-seat workshop → assert 20 ACTIVE, 30 `WORKSHOP_FULL`, `seats_taken == 20 == count(ACTIVE)`. Second scenario: concurrent cancels of the same row → exactly one wins. Third: concurrent register + capacity edit never violates BR-1/BR-4. |
| 2.7 | Service tests: duplicate email (case-insensitive), register on cancelled/past workshop, capacity edit below taken, stale version, history includes cancelled rows with actor and time. |
| 2.8 | `DemoWorkshopSeeder` (demo profile only; Java, because rows need the demo staff that are created after Flyway runs): 8–10 workshops across 3 locations covering open / 1 seat left / full / cancelled / past / next week, plus a few registrations (some cancelled). |
| 2.9 | Fill the remaining `AccessMatrixTest` rows. Review the diff for access control (every endpoint has a policy and a matrix row) and for concurrency safety on the seat paths. |

**Done when:** all P2 tests and the full matrix are green in CI, and a
Postman/HTTPie run shows the conflict codes.

---

## P3: Frontend end to end (50 min). SRS UI-1…5, FR-ACL-03

| # | Task |
|---|---|
| 3.1 | `core/config` runtime config (`/config.json`), `core/auth` (keycloak-angular `provideKeycloak`, `includeBearerTokenInterceptor` for `/api`, `SessionStore` loading `/me`, `roleGuard(...roles)`, landing redirect by role), `core/http/problem.interceptor.ts` mapping `code` to plain-language messages. |
| 3.2 | `core/layout`: shell, top bar with name, role and sign-out, role-aware nav, toast host. |
| 3.0 | Design tokens (architecture §10): CSS custom properties for light/dark in `src/styles/tokens.css`, mapped into `tailwind.config.js` (`canvas`, `surface`, `ink`, `line`, `accent`, `ok`, `warn`, `full`, `quiet`…), fonts `Source Serif 4` + `Inter` (self-hosted via `@fontsource`, so no external font CDN), base typography, focus ring, theme toggle. No raw hex values in components. |
| 3.3 | `shared/ui`: button (primary/secondary/ghost), status badge, seat meter, confirm dialog, empty state, pager, all on the tokens. |
| 3.4 | `features/workshops`: list page (table + basic date range; full filters come in P4), detail page (info, seat meter, 15 s seat refresh while visible), create/edit form (Manager; typed reactive form; server-side `STALE_VERSION` / `CAPACITY_BELOW_TAKEN` shown inline), cancel-workshop confirm. |
| 3.5 | `features/registrations`: register panel on the detail page (name + email; on `WORKSHOP_FULL` shows the plain-language message, then the waitlist offer once B2 exists), registration history table (status chips, registered by/at, cancelled by/at and reason), cancel dialog with optional reason. |
| 3.6 | `features/staff-accounts` (Admin): list, create form, edit role / deactivate / reactivate, reset password. |
| 3.7 | Jest: `roleGuard`, problem interceptor mapping, `SessionStore`. Playwright smoke: login as each role; Staff books then cancels. |
| 3.8 | Manual smoke pass per role against Compose, plus a UX review of the touched screens. |

**Done when:** acceptance steps 1–6 in SRS §8 pass by hand on the Compose
stack, and the Playwright smoke passes.

---

## P4: Finding workshops (15 min). SRS FR-FIND

| # | Task |
|---|---|
| 4.1 | `search` module skeleton (`package-info` `@ApplicationModule`, allowed deps `workshops`, `registrations` (events only), `common`). `WorkshopSearch` interface + `WorkshopSearchCriteria`. Move `GET /api/v1/workshops` into `search.internal.WorkshopSearchController` (same `CAN_VIEW_CATALOGUE` policy and matrix row). |
| 4.2 | `JpaWorkshopSearch` (fallback and reference): JPA Specification. Date range on `starts_at` (inclusive day boundaries in the centre's timezone), derived status → predicates, `hasSeats`, `locationId`, `q` (ILIKE on code/title/instructor/location). Page + sort. Tests for each filter and the week boundary. |
| 4.3 | `MeiliWorkshopSearch`: `meilisearch-java` client bean (`SEATWISE_SEARCH_URL`, `SEATWISE_SEARCH_MASTER_KEY`), `SearchIndexBootstrap` (apply settings per architecture §7a, derive scoped key, full reindex on `ApplicationReadyEvent`, nightly `@Scheduled` reconcile), `WorkshopIndexer` (`@TransactionalEventListener(AFTER_COMMIT)` on workshop and registration events → re-read from `WorkshopCatalogue` → upsert), criteria → Meilisearch filter string, then hydrate the page's seat counts and lifecycle from Postgres. `FallbackWorkshopSearch` routes to JPA when Meilisearch errors or times out (500 ms), setting `searchMode`. |
| 4.4 | `WorkshopSearchIT` (Testcontainers Postgres + `getmeili/meilisearch:v1.43.0`): filter parity between index and fallback on demo data; "potery" finds Pottery; booking the last seat updates `seatsLeft` in the index after commit (Awaitility ≤ 2 s); a rolled-back booking never reaches the index; stale index + hydration still shows exact seats; Meilisearch stopped → fallback. |
| 4.5 | Frontend: filter bar (presets Today / This week / Next 7 days / Custom, status multi-select, location, "Has seats" toggle, search-as-you-type with 200 ms debounce). Filters ↔ URL query params. Default `?preset=this-week&hasSeats=true` for Manager/Staff. Subtle "basic search mode" note when `searchMode = fallback`. |

**Done when:** "This week · has seats" returns exactly the matching demo
workshops (asserted in a test and by eye), typo search works, and stopping
the `search` container degrades to basic mode instead of an error.

---

## P5: Hand-in polish (runs alongside each phase, last 5 min)

- `README.md`: prerequisites; `cp .env.example .env && docker compose up -d
  --build`; URLs; **demo logins** (dev-only); dev-loop mode; how to run tests;
  link to the live URL.
- `docs/design-notes.md`: **one page**. Stack and why; key design decisions;
  trade-offs; assumptions (SRS §2.5); **how over-registration is prevented**
  (architecture §7, condensed to about 8 lines); what was skipped.
- `CHANGELOG.md` entry, cleaned-up `TASKS.md` / `BACKLOG.md`.

---

## B1: Bonus, audit trail (25 min). SRS FR-AUD

| # | Task |
|---|---|
| B1.1 | `V4__audit.sql`: `audit_event` + immutability trigger + index. |
| B1.2 | `audit.internal.AuditRecorder`: synchronous `@EventListener` for every event record. Events carry before/after snapshots so `changes` jsonb holds only the changed fields. |
| B1.3 | `AuditTrail` query + `GET /audit-events` with role-scoped entity types; add the matrix rows. |
| B1.4 | Test: a workshop edit writes exactly one event with the right diff; a rolled-back edit writes none. |
| B1.5 | Frontend `features/activity`: timeline component on workshop detail (Manager/Staff) and an "Account activity" page (Admin). |

## B2: Bonus, waitlist (30 min). SRS FR-WL

| # | Task |
|---|---|
| B2.1 | `register(joinWaitlistIfFull=true)` → `WAITLISTED` when the claim fails because the workshop is full (not when it's closed). |
| B2.2 | `cancel()` of an ACTIVE row → select the oldest WAITLISTED row `FOR UPDATE SKIP LOCKED` → promote it (`ACTIVE`, `promoted_at`). The seat transfers without touching `seats_taken`; publish `WaitlistPromoted`. If nobody is waiting → `releaseSeat`. |
| B2.3 | `waitlistCount` and waitlist position in views. |
| B2.4 | Tests: promotion order; concurrent cancels promote two different people; capacity is never exceeded; removing someone from the waitlist doesn't release a seat. |
| B2.5 | UI: "Add to waitlist instead?" on a full workshop; waitlist section; "Promoted, call to confirm" badge. |

## D: Delivery (30 min)

Follow `plans/cicd-dokploy.md` §4–6: `deliver.yml`, GHCR, Dokploy apps and
env, the first deploy, live smoke, and the live URL in the README.

---

## Working agreement

- Each phase gets its own `plans/<phase>.md` spec only if it turns out to be
  ambiguous; otherwise this file is the spec.
- Every phase: compile → tests → code review (+ an access-control review if
  endpoints changed, + a concurrency review if seat paths changed) → push the
  PR → CI green → merge.
- Non-negotiables: backend-enforced permission matrix with a test row per
  endpoint; seats only claimed/released by atomic SQL with the DB `CHECK`
  intact; registrations and audit events never deleted.
- Commits are atomic per logical change (`feat:`, `fix:`, `chore:`, `docs:`,
  `test:`, `ci:`).

## Definition of done (whole project)

- [ ] SRS §8 acceptance script passes on Compose **and** on the live Dokploy URL
- [ ] CI green: modularity, access matrix, concurrency, service and repository tests; frontend lint, unit, build
- [ ] README lets a stranger run it in under 5 minutes; demo logins listed
- [ ] `docs/design-notes.md` fits on one page and lists what was skipped
- [ ] No secrets in git; `.env.example` complete
