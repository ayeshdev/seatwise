import { isRole, roleLabel } from '@core/auth/role';
import { formatDateTime } from '@features/workshops/workshop-format';

import { AuditAction } from './audit.models';

/** How an activity entry reads on screen: plain words, never raw field names or enum casing. */

const FIELD_LABELS: Record<string, string> = {
  capacity: 'Capacity',
  title: 'Title',
  instructor: 'Instructor',
  startsAt: 'Starts',
  endsAt: 'Ends',
  location: 'Location',
  locationId: 'Location',
  code: 'Code',
  description: 'Description',
  role: 'Role',
  fullName: 'Full name',
  active: 'Account status',
  status: 'Status',
  attendeeName: 'Attendee name',
  attendeeEmail: 'Attendee email',
};

/** "seatsTaken" becomes "Seats taken". */
function humanize(key: string): string {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? humanize(field);
}

const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

export const NOT_SET = 'Not set';

/** One side of a change: dates as "Sat 17 Oct, 09:30", booleans as words, empty as "Not set". */
export function formatChangeValue(field: string, value: unknown, locale?: string): string {
  if (value === null || value === undefined || value === '') {
    return NOT_SET;
  }
  if (typeof value === 'boolean') {
    if (field === 'active') {
      return value ? 'Active' : 'Inactive';
    }
    return value ? 'Yes' : 'No';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (typeof value === 'string') {
    if (ISO_INSTANT.test(value) && !Number.isNaN(new Date(value).getTime())) {
      return formatDateTime(value, locale);
    }
    if (field === 'role' && isRole(value)) {
      return roleLabel(value);
    }
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((v) => formatChangeValue(field, v, locale)).join(', ');
  }
  return JSON.stringify(value);
}

export type ActionTone = 'neutral' | 'full' | 'ok';

const ACTIONS: Record<AuditAction, { label: string; tone: ActionTone }> = {
  CREATED: { label: 'Created', tone: 'neutral' },
  UPDATED: { label: 'Edited', tone: 'neutral' },
  CANCELLED: { label: 'Cancelled', tone: 'full' },
  ROLE_CHANGED: { label: 'Role changed', tone: 'neutral' },
  RENAMED: { label: 'Renamed', tone: 'neutral' },
  DEACTIVATED: { label: 'Deactivated', tone: 'full' },
  REACTIVATED: { label: 'Reactivated', tone: 'ok' },
  PASSWORD_RESET: { label: 'Password reset', tone: 'neutral' },
  REGISTERED: { label: 'Registered', tone: 'neutral' },
  WAITLISTED: { label: 'Waitlisted', tone: 'neutral' },
  PROMOTED: { label: 'Moved off the waitlist', tone: 'ok' },
};

function known(action: string): { label: string; tone: ActionTone } | undefined {
  return (ACTIONS as Record<string, { label: string; tone: ActionTone } | undefined>)[action];
}

export function actionLabel(action: string): string {
  return known(action)?.label ?? humanize(action);
}

export function actionTone(action: string): ActionTone {
  return known(action)?.tone ?? 'neutral';
}

const UNITS: { unit: Intl.RelativeTimeFormatUnit; seconds: number }[] = [
  { unit: 'year', seconds: 365 * 24 * 3600 },
  { unit: 'month', seconds: 30 * 24 * 3600 },
  { unit: 'day', seconds: 24 * 3600 },
  { unit: 'hour', seconds: 3600 },
  { unit: 'minute', seconds: 60 },
];

/** "just now", "5 minutes ago", "yesterday". */
export function formatRelative(iso: string, now: Date = new Date(), locale?: string): string {
  const seconds = Math.round((new Date(iso).getTime() - now.getTime()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) {
    return 'just now';
  }
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  for (const { unit, seconds: size } of UNITS) {
    if (abs >= size || unit === 'minute') {
      return formatter.format(Math.round(seconds / size), unit);
    }
  }
  return 'just now';
}
