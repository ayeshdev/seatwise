# Tasks

Rolling execution window. Pick from the top and delete lines when shipped.
Full detail: `plans/implementation-plan.md`.

## Now
- [ ] Access matrix: assert each handler's exact `Policies` constant per row; invalid-body variants for denied roles on POST/PUT/PATCH (expect 403)
- [ ] P1 Access control: `staff_account`, `/me`, `Policies`, `StaffAuthenticationConverter`, Keycloak provisioner, admin bootstrap, `AccessMatrixTest`
- [ ] P2 Workshops + capacity rule + registrations + history + `CapacityConcurrencyIT` + demo seed
- [ ] P3 Frontend end to end on the warm/minimal design tokens (auth, shell, workshops, registrations, staff accounts)
- [ ] P4 Finding workshops: `search` module, Postgres fallback first, then Meilisearch index + after-commit sync, filters, presets, URL state
- [ ] P5 README, `docs/design-notes.md`, skipped list

## Next (bonus, in order)
- [ ] B1 Audit trail
- [ ] B2 Waitlist
- [ ] D Delivery: `deliver.yml`, Dokploy setup, live smoke
- [ ] B1 backend contract notes: Manager/Staff `GET /audit-events` with no entityType returns WORKSHOP + REGISTRATION events; `from`/`to` are inclusive `YYYY-MM-DD` in the centre timezone
