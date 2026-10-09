/** Shared test data for the activity specs. Test-only: nothing in the app imports this file. */
import type { AuditEvent, AuditPage } from './audit.models';

export const AUDIT_URL = '/api/v1/audit-events';

export function makeEvent(overrides: Partial<AuditEvent> = {}): AuditEvent {
  return {
    id: 1,
    occurredAt: new Date(2030, 9, 17, 9, 30).toISOString(),
    actor: { id: 'staff-1', fullName: 'Amara Silva' },
    entityType: 'WORKSHOP',
    entityId: 'w-1',
    workshopId: 'w-1',
    action: 'UPDATED',
    summary: 'Changed capacity from 12 to 16',
    changes: { capacity: { from: 12, to: 16 } },
    ...overrides,
  };
}

export function makePage(
  items: AuditEvent[],
  overrides: Partial<AuditPage> = {},
): AuditPage {
  return { items, page: 0, size: 20, totalItems: items.length, ...overrides };
}
