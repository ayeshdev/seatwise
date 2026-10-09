# Seatwise: design notes

**Stack and why.** Spring Boot 4 (Java 25) with Spring Modulith, PostgreSQL 17,
Keycloak 26, Meilisearch, and Angular 20 with Tailwind, all in Docker Compose. This is
the stack I know best and run in production, so the time went into the domain.
PostgreSQL does the hard part: row locks, `CHECK` constraints and partial unique
indexes let the database itself guarantee the capacity rule. Keycloak removes all
password handling from our code. Modulith keeps five small modules (`accounts`,
`workshops`, `registrations`, `audit`, `search`) honest without microservices.

**How over-registration is prevented.** A seat is claimed with one atomic
statement inside the booking transaction:
`UPDATE workshop SET seats_taken = seats_taken + 1 WHERE id = ? AND lifecycle = 'SCHEDULED' AND starts_at > ? AND seats_taken < capacity`.
If it changes no row, the workshop is full (or closed) and nothing is inserted.
Concurrent bookings for the same workshop queue on that row lock, and each one
re-checks `seats_taken < capacity` against the committed value. There is no
"read, check in Java, then write" gap, and that gap is exactly where two people
on the phone both promised the last seat. Underneath, `CHECK (seats_taken BETWEEN 0 AND capacity)`
makes an over-capacity commit impossible even for a future code bug. A partial
unique index stops one person (or one double-click) holding two seats. Every
seat path takes the workshop lock first, so there are no deadlocks. A
Testcontainers test fires 50 parallel bookings at 20 seats on every build and
gets exactly 20. A Playwright test does the same with two real browsers.

**Access control.** Keycloak *authenticates*. The app database *authorizes*. On
every request the token's `sub` is mapped to a `staff_account` row and exactly
one role, so a role change or deactivation applies to that person's very next
request, not when their token expires. Every endpoint has a coarse URL rule
plus a method-level `@PreAuthorize`. `AccessMatrixTest` checks every endpoint
against every role and fails the build if an endpoint is added without a rule or
a test row. The UI hides what a role can't do, but only for clarity.

**History.** Registrations are never deleted: there are no delete methods, a
database trigger refuses deletes, and cancelling records who, when and why.
Workshop edits, account and role changes and bookings are written to an
immutable `audit_event` table *in the same transaction* as the change, so the
trail can't miss a committed change or record one that rolled back.

**Design decisions and trade-offs.**
- `seats_taken` is a small denormalized counter. It's kept honest by being the
  only write path (it can't be written through JPA), by the `CHECK`, and by tests.
- Workshop status is **derived** (open, full, in progress, completed) rather
  than stored. It can never go stale, at the cost of computed filters.
- The waitlist auto-promotes the next person when a seat frees, and flags them
  "call to confirm". Attendees have no accounts, so staff phone them anyway.
- Meilisearch gives typo-tolerant search ("potery" still finds Pottery) but
  is only a derived index. Seat counts on each results page are re-read from
  PostgreSQL. If the index is down, the search falls back to plain SQL, and
  bookings never touch the index.
- The seat count on screen refreshes every 15 s by polling. The API is
  authoritative, so a stale screen can never overbook.

**Assumptions.**
- The permission matrix is literal, so an Admin can't view workshops (`403`).
- Each person has one role.
- All locations share one timezone (configurable).
- An email can hold one live booking per workshop.
- Bookings close when a workshop starts.
- Cancelling a workshop keeps its bookings and blocks new ones.

Open questions for the client are listed in the SRS, §10.

**What I skipped or kept simple.**
- No email or SMS to attendees.
- No offer-with-deadline flow for the waitlist.
- No server-sent events for live seat counts (polling instead).
- No CSV export.
- Single timezone only.
- Live deployment (GitHub Actions → GHCR → Dokploy) is planned in
  `plans/cicd-dokploy.md`, but it's optional and not part of this hand-in.
