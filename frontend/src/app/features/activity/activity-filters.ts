import { ParamMap, Params } from '@angular/router';

import { AuditEntityType } from './audit.models';

/**
 * The activity screens' filters live in the address bar so a view can be bookmarked:
 *   show=workshops|bookings   (Activity only; absent means everything)
 *   from=YYYY-MM-DD&to=YYYY-MM-DD
 */

export type ActivityKind = 'workshops' | 'bookings';

export const ACTIVITY_KINDS: readonly { value: ActivityKind | null; label: string }[] = [
  { value: null, label: 'All activity' },
  { value: 'workshops', label: 'Workshop changes' },
  { value: 'bookings', label: 'Bookings' },
];

const KIND_TO_ENTITY: Record<ActivityKind, AuditEntityType> = {
  workshops: 'WORKSHOP',
  bookings: 'REGISTRATION',
};

export interface DateRange {
  from: string | null;
  to: string | null;
}

export interface ActivityFilters extends DateRange {
  kind: ActivityKind | null;
}

export function entityTypeFor(kind: ActivityKind | null): AuditEntityType | null {
  return kind === null ? null : KIND_TO_ENTITY[kind];
}

function validDate(value: string | null): string | null {
  if (value === null || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  return Number.isNaN(new Date(`${value}T00:00:00`).getTime()) ? null : value;
}

export function rangeFromParams(params: ParamMap): DateRange {
  let from = validDate(params.get('from'));
  let to = validDate(params.get('to'));
  if (from !== null && to !== null && from > to) {
    [from, to] = [to, from];
  }
  return { from, to };
}

export function filtersFromParams(params: ParamMap): ActivityFilters {
  const show = params.get('show');
  const kind: ActivityKind | null = show === 'workshops' || show === 'bookings' ? show : null;
  return { kind, ...rangeFromParams(params) };
}

/** `null` removes a parameter from the address bar when passed to `Router.navigate`. */
export function rangeToParams(range: DateRange): Params {
  return { from: range.from, to: range.to };
}

export function filtersToParams(filters: ActivityFilters): Params {
  return { show: filters.kind, ...rangeToParams(filters) };
}
