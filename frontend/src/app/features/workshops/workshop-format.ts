import { toIsoDate } from './workshop-filters';

/**
 * How dates and times read on screen: the browser's own time zone and locale, via Intl, never
 * a raw ISO string. Pass `locale` only in tests; leave it out to follow the browser.
 */

function datePart(date: Date, locale?: string): string {
  // "Sat 17 Oct": some engines add a comma after the weekday, so drop it.
  return new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short' })
    .format(date)
    .replace(',', '');
}

function timePart(date: Date, locale?: string): string {
  return new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date);
}

function sameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  );
}

/** "Sat 17 Oct" */
export function formatDay(iso: string, locale?: string): string {
  return datePart(new Date(iso), locale);
}

/** "09:30–12:00" (same day) or "22:00 – Sun 18 Oct, 01:00" (crosses midnight). */
export function formatTimeRange(startsAt: string, endsAt: string, locale?: string): string {
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  return sameDay(start, end)
    ? `${timePart(start, locale)}–${timePart(end, locale)}`
    : `${timePart(start, locale)} – ${datePart(end, locale)}, ${timePart(end, locale)}`;
}

/** "Sat 17 Oct, 09:30–12:00" */
export function formatWhen(startsAt: string, endsAt: string, locale?: string): string {
  return `${formatDay(startsAt, locale)}, ${formatTimeRange(startsAt, endsAt, locale)}`;
}

/** "Sat 17 Oct, 09:30" */
export function formatDateTime(iso: string, locale?: string): string {
  const date = new Date(iso);
  return `${datePart(date, locale)}, ${timePart(date, locale)}`;
}

/** Local `YYYY-MM-DD` for a date input, from an ISO instant. */
export function localDateValue(iso: string): string {
  return toIsoDate(new Date(iso));
}

/** Local `HH:mm` for a time input, from an ISO instant. */
export function localTimeValue(iso: string): string {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/** Combines a local date (`YYYY-MM-DD`) and time (`HH:mm`) into an ISO instant, or null if either is unusable. */
export function toIsoInstant(date: string, time: string): string | null {
  const d = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const t = /^(\d{2}):(\d{2})$/.exec(time);
  if (d === null || t === null) {
    return null;
  }
  const local = new Date(
    Number(d[1]),
    Number(d[2]) - 1,
    Number(d[3]),
    Number(t[1]),
    Number(t[2]),
  );
  return Number.isNaN(local.getTime()) ? null : local.toISOString();
}

/** First word of a full name ("Priya Shah" gives "Priya"), for friendly button text. */
export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? fullName;
}
