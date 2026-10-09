import type { StaffRef } from '@features/workshops/workshop.models';
import type { RegistrationStatus } from '@shared/ui/status-badge';

export type { RegistrationStatus };

/** Shapes follow the "Payload shapes" block in docs/architecture.md section 9. */

export interface Registration {
  id: string;
  workshopId: string;
  attendeeName: string;
  attendeeEmail: string;
  status: RegistrationStatus;
  registeredAt: string;
  registeredBy: StaffRef;
  /** Set when the attendee was moved off the waitlist. */
  promotedAt: string | null;
  /** 1-based; only while WAITLISTED. */
  waitlistPosition: number | null;
  cancelledAt: string | null;
  cancelledBy: StaffRef | null;
  cancellationReason: string | null;
}

export interface RegisterRequest {
  attendeeName: string;
  attendeeEmail: string;
  joinWaitlistIfFull?: boolean;
}

export interface CancelRequest {
  reason?: string | null;
}

export interface CancelResult {
  cancelled: Registration;
  promoted: Registration | null;
}
