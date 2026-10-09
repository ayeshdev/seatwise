# Backlog

Parked ideas and deferrals, each with enough context to decide later.
Move an item to `TASKS.md` when it's prioritized, then delete it here.

## Product
- [ ] Live seat updates via SSE instead of 15 s polling. Polling is enough for ~12 concurrent desk users; revisit if staff report stale counts.
- [ ] Waitlist "offer with deadline" flow instead of auto-promote (SRS open question 4).
- [ ] Bulk-cancel registrations when a workshop is cancelled (SRS open question 3).
- [ ] Attendee lookup across workshops by email ("is Priya booked on anything?").
- [ ] CSV export of a workshop's attendee list for instructors.

## Engineering
- [ ] Pin GitHub Actions by commit SHA (Dependabot keeps them fresh).
- [ ] Playwright visual snapshots for the workshop list and detail.
- [ ] Rate limit on the registration endpoint (only needed if exposed beyond staff).
