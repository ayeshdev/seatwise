import { ParamMap, Params } from '@angular/router';

import type { WorkshopSearchQuery, WorkshopStatus } from './workshop.models';

/**
 * The list screen's filters, and how they map to the address bar (so a view can be bookmarked):
 *
 *   preset=today|this-week|next-7-days|custom   (always written)
 *   from=YYYY-MM-DD&to=YYYY-MM-DD               (only with preset=custom)
 *   status=OPEN&status=FULL                     (repeated; omitted = any status)
 *   locationId=<id>   hasSeats=true   q=<text>
 *   page=<n>                                    (zero-based, omitted on the first page)
 *
 * With no parameters at all the list opens on "this week, still has seats".
 */
export type DatePreset = 'today' | 'this-week' | 'next-7-days' | 'custom';

export const DATE_PRESETS: readonly { value: DatePreset; label: string }[] = [
  { value: 'today', label: 'Today' },
  { value: 'this-week', label: 'This week' },
  { value: 'next-7-days', label: 'Next 7 days' },
  { value: 'custom', label: 'Custom' },
];

export const STATUS_OPTIONS: readonly { value: WorkshopStatus; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'FULL', label: 'Full' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export const DEFAULT_PRESET: DatePreset = 'this-week';
export const PAGE_SIZE = 20;

export interface WorkshopFilters {
  preset: DatePreset;
  /** `YYYY-MM-DD`; only meaningful for the custom preset. */
  from: string | null;
  to: string | null;
  statuses: WorkshopStatus[];
  locationId: string | null;
  hasSeats: boolean;
  q: string;
  page: number;
}

export function defaultFilters(): WorkshopFilters {
  return {
    preset: DEFAULT_PRESET,
    from: null,
    to: null,
    statuses: [],
    locationId: null,
    hasSeats: true,
    q: '',
    page: 0,
  };
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function isPreset(value: string | null): value is DatePreset {
  return DATE_PRESETS.some((p) => p.value === value);
}

function isStatus(value: string): value is WorkshopStatus {
  return STATUS_OPTIONS.some((s) => s.value === value);
}

function validDate(value: string | null): string | null {
  return value !== null && DATE_PATTERN.test(value) ? value : null;
}

/** Reads the address bar. Unknown or malformed values are ignored rather than trusted. */
export function filtersFromParams(params: ParamMap): WorkshopFilters {
  if (params.keys.length === 0) {
    return defaultFilters();
  }
  const from = validDate(params.get('from'));
  const to = validDate(params.get('to'));
  const presetParam = params.get('preset');
  const preset: DatePreset = isPreset(presetParam)
    ? presetParam
    : from !== null || to !== null
      ? 'custom'
      : DEFAULT_PRESET;

  const statuses = params
    .getAll('status')
    .filter(isStatus)
    .filter((status, index, all) => all.indexOf(status) === index);

  const page = Number.parseInt(params.get('page') ?? '', 10);

  return {
    preset,
    from: preset === 'custom' ? from : null,
    to: preset === 'custom' ? to : null,
    statuses,
    locationId: params.get('locationId') || null,
    hasSeats: params.get('hasSeats') === 'true',
    q: (params.get('q') ?? '').trim(),
    page: Number.isFinite(page) && page > 0 ? page : 0,
  };
}

/** The address-bar form of the filters; navigate with these and nothing else. */
export function filtersToParams(filters: WorkshopFilters): Params {
  const params: Params = { preset: filters.preset };
  if (filters.preset === 'custom') {
    if (filters.from) {
      params['from'] = filters.from;
    }
    if (filters.to) {
      params['to'] = filters.to;
    }
  }
  if (filters.statuses.length > 0) {
    params['status'] = [...filters.statuses];
  }
  if (filters.locationId) {
    params['locationId'] = filters.locationId;
  }
  if (filters.hasSeats) {
    params['hasSeats'] = 'true';
  }
  if (filters.q.trim() !== '') {
    params['q'] = filters.q.trim();
  }
  if (filters.page > 0) {
    params['page'] = String(filters.page);
  }
  return params;
}

/** `YYYY-MM-DD` in the browser's local calendar (never via UTC, which can shift the day). */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** Monday to Sunday of the week containing `now`, in local time. */
export function weekRange(now: Date): { from: string; to: string } {
  const sinceMonday = (now.getDay() + 6) % 7;
  const monday = addDays(now, -sinceMonday);
  return { from: toIsoDate(monday), to: toIsoDate(addDays(monday, 6)) };
}

/** The inclusive date range a preset stands for; `null` ends are open. */
export function resolveDateRange(
  filters: Pick<WorkshopFilters, 'preset' | 'from' | 'to'>,
  now: Date = new Date(),
): { from: string | null; to: string | null } {
  switch (filters.preset) {
    case 'today':
      return { from: toIsoDate(now), to: toIsoDate(now) };
    case 'this-week':
      return weekRange(now);
    case 'next-7-days':
      return { from: toIsoDate(now), to: toIsoDate(addDays(now, 6)) };
    case 'custom':
      return { from: filters.from, to: filters.to };
  }
}

/** What to ask the search endpoint for. */
export function toSearchQuery(
  filters: WorkshopFilters,
  now: Date = new Date(),
): WorkshopSearchQuery {
  const { from, to } = resolveDateRange(filters, now);
  return {
    from,
    to,
    statuses: filters.statuses,
    locationId: filters.locationId,
    hasSeats: filters.hasSeats,
    q: filters.q,
    page: filters.page,
    size: PAGE_SIZE,
  };
}
