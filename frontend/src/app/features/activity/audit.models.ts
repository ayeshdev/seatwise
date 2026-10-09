/** Mirrors the `AuditEvent` contract in docs/architecture.md section 9. */

export type AuditEntityType = 'STAFF_ACCOUNT' | 'WORKSHOP' | 'REGISTRATION';

export type AuditAction =
  | 'CREATED'
  | 'UPDATED'
  | 'CANCELLED'
  | 'ROLE_CHANGED'
  | 'RENAMED'
  | 'DEACTIVATED'
  | 'REACTIVATED'
  | 'PASSWORD_RESET'
  | 'REGISTERED'
  | 'WAITLISTED'
  | 'PROMOTED';

export interface AuditActor {
  id: string;
  fullName: string;
}

export interface AuditChange {
  from: unknown;
  to: unknown;
}

export interface AuditEvent {
  id: number;
  occurredAt: string;
  /** `null` means the system itself (bootstrap, seeding). */
  actor: AuditActor | null;
  entityType: AuditEntityType;
  entityId: string;
  /** Set for workshop and booking events, so a workshop's timeline includes its bookings. */
  workshopId: string | null;
  action: AuditAction;
  /** A plain-language one-liner, e.g. "Changed capacity from 12 to 16". */
  summary: string;
  /** Empty object when not applicable. */
  changes: Record<string, AuditChange>;
}

export interface AuditPage {
  items: AuditEvent[];
  /** Zero-based. */
  page: number;
  size: number;
  totalItems: number;
}

export interface AuditQuery {
  entityType?: AuditEntityType | null;
  entityId?: string | null;
  workshopId?: string | null;
  /** `YYYY-MM-DD`, inclusive; the centre's local day. */
  from?: string | null;
  to?: string | null;
  page: number;
  size: number;
}
