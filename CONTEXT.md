# Project Context

## Project stance

- **Stage:** Greenfield. There is no production data yet, so a clean break
  beats a compatibility shim. Once deployed, migrations are forward-only.
- **Effort framing:** a 3-hour core build. A working, connected app beats a
  feature-heavy broken one. Cut SHOULDs before compromising a MUST.

## Project shape

**Seatwise** is a workshop registration service for a community training
centre.
- **Backend:** Java 25 / Spring Boot 4 / Spring Modulith (`com.seatwise`:
  `accounts`, `workshops`, `registrations`, `audit`, `common`).
- **Frontend:** Angular 20 single app `seatwise-desk` (`frontend/`).
- **Database:** PostgreSQL 17 (Flyway).
- **Identity:** Keycloak 26, realm `seatwise`.
- **Delivery:** GitHub Actions → GHCR → Dokploy.

## Enabled stack layers

```
spring, spring-modulith, postgres, flyway, angular, keycloak, docker, github-actions, dokploy
```

## Hard project-wide invariants

- **Backend-enforced access control:** every endpoint has a `Policies` rule
  and an `AccessMatrixTest` row.
- **Capacity rule:** active registrations ≤ capacity, enforced by atomic SQL
  and a DB `CHECK`, and proven by `CapacityConcurrencyIT`.
- **History is permanent:** registrations and audit events are never deleted.
- **Module boundaries:** verified by `ModularityTests`.
- **CI green before merge;** deploy only when asked.
