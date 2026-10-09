# Seatwise

A workshop registration service for a community training centre with three
locations and about 15 staff. Front-desk staff register attendees, the
programme manager schedules workshops, and administrators look after staff
accounts.

**A workshop can never hold more active registrations than its capacity, even
when several people book the last seat at the same moment.** Cancelled
registrations free the seat but are never deleted, and the full history of who
registered or cancelled, and when, is always visible.

- One-page design summary: [docs/design-notes.md](docs/design-notes.md)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Requirements (SRS): [docs/srs.md](docs/srs.md)

---

## Run it locally (about 3 minutes)

Requires **Docker** with Compose v2 (Docker Desktop on Windows or macOS). Nothing
else needs to be installed.

```bash
git clone <this repo> seatwise && cd seatwise
cp .env.example .env              # optional: every value has a working default
docker compose up -d --build --wait
```

The first build downloads images and dependencies, so it takes a few minutes.
Then open **http://localhost:4280**.

### Demo logins (dev only)

| Role | Email | Password | Lands on |
|---|---|---|---|
| Admin | `admin@seatwise.local` | `Admin#Seatwise1` | Staff accounts |
| Programme manager | `manager@seatwise.local` | `Manager#Seatwise1` | Workshops, this week with seats |
| Front desk | `staff@seatwise.local` | `Staff#Seatwise1` | Workshops, this week with seats |

The first Admin is created automatically on first start, as the brief requires
(there is no public sign-up). The demo Manager, the demo Staff member and 10 sample workshops
are seeded by the `demo` profile. The workshops cover open, almost full, full
with a waitlist, cancelled and past cases, plus bookings with some cancellations.

### What to try

1. As **Staff**, open a workshop with one seat left. Open the same workshop in a
   second browser (or a private window, signed in as the Manager) and press
   **Register** in both at the same time. One booking succeeds. The other is
   told the last seat was just taken and offered the waitlist.
2. Cancel a booking with a reason. The seat is freed, the row stays in the
   history with "Cancelled by … at …", and if anyone was waiting they move up.
3. As **Manager**, try to lower a workshop's capacity below the seats already
   taken. It is refused, with the count.
4. As **Admin**, create a front-desk account, then change its role or deactivate it.
   The change applies to that person's very next request.
5. Try something your role isn't allowed to do, from the UI or straight
   against the API. The backend refuses it with `403`.

### Services and ports

| Service | URL | Notes |
|---|---|---|
| Desk (Angular, nginx) | http://localhost:4280 | Proxies `/api` to the API, so there is one origin |
| API (Spring Boot) | http://localhost:8280 | `GET /actuator/health`, OpenAPI at `/api/v1/openapi.json` |
| Keycloak | http://localhost:8281 | Admin console: `admin` / `admin` (dev only) |
| Meilisearch | http://localhost:7710 | Search index, rebuilt from Postgres on every start |
| PostgreSQL | localhost:5442 | `seatwise` / `seatwise` |

Stop with `docker compose down`. Add `-v` to also wipe the data.

---

## Developer loop (optional)

Run the infrastructure in Docker and the apps on the host for fast reloads.
This needs JDK 25, Node 22+ and pnpm 9.

```bash
docker compose up -d db idp search --wait
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=dev,demo --server.port=8280'   # API on :8280
pnpm -C frontend install && pnpm -C frontend start                                 # desk on :4210
```

The dev server on http://localhost:4210 proxies `/api` to the API.

## Tests

```bash
./backend/gradlew -p backend build        # all backend tests (needs Docker for Testcontainers)
pnpm -C frontend lint && pnpm -C frontend test && pnpm -C frontend build
pnpm -C frontend e2e                      # Playwright journeys against http://localhost:4280
```

Tests worth reading first:

- `CapacityConcurrencyIT`: real PostgreSQL. 50 parallel bookings against 20
  seats give exactly 20 registrations. Racing cancellations, capacity edits and
  waitlist promotions are tested too.
- `AccessMatrixTest`: every endpoint against every role, plus anonymous. A new
  endpoint without a permission rule or a test row fails the build.
- `e2e/last-seat.spec.ts`: two browsers book the last seat at the same moment.

## Tech stack

Java 25 · Spring Boot 4 · Spring Modulith · PostgreSQL 17 · Flyway · Keycloak 26 ·
Meilisearch · Angular 20 · Tailwind CSS · Docker Compose · GitHub Actions.
Why each was chosen is in [docs/design-notes.md](docs/design-notes.md).

## Repository layout

```
backend/    Spring Boot API: modules accounts, workshops, registrations, audit, search, common
frontend/   Angular app "Seatwise Desk" (+ nginx image, Playwright e2e)
infra/      Keycloak realm and production image, Postgres init script
docs/       design notes, architecture, SRS
plans/      implementation and CI/CD plans
.github/    CI workflow
```
