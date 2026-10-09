# Seatwise

Workshop registration service for a community training centre. Front-desk
staff register attendees, programme managers schedule workshops, and
administrators manage staff accounts. A workshop can never be overbooked, even
when several people book the last seat at the same moment.

> Work in progress. Full setup notes, demo logins and the design summary land
> with the first complete build.

## Quick start

Requires Docker Desktop (or Docker Engine with Compose v2).

```bash
cp .env.example .env        # optional: every value has a working default
docker compose up -d --build --wait
```

| Service | URL |
|---|---|
| Seatwise Desk | http://localhost:4280 |
| API | http://localhost:8280 (health: `/actuator/health`) |
| Keycloak | http://localhost:8281 |
| Meilisearch (dev only) | http://localhost:7710 |

## Documentation

- [Architecture](docs/architecture.md)
- [Software requirements specification](docs/srs.md)
- [Implementation plan](plans/implementation-plan.md)
- [CI/CD plan](plans/cicd-dokploy.md)
