import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigStore } from '@core/config/runtime-config';

import {
  Location,
  Workshop,
  WorkshopRequest,
  WorkshopSearchQuery,
  WorkshopSearchResult,
} from './workshop.models';

/** The one place the workshop screens talk to the backend. */
@Injectable({ providedIn: 'root' })
export class WorkshopsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigStore);

  search(query: WorkshopSearchQuery): Observable<WorkshopSearchResult> {
    return this.http.get<WorkshopSearchResult>(this.url('/v1/workshops'), {
      params: searchParams(query),
    });
  }

  get(id: string): Observable<Workshop> {
    return this.http.get<Workshop>(this.url(`/v1/workshops/${encodeURIComponent(id)}`));
  }

  create(request: WorkshopRequest): Observable<Workshop> {
    return this.http.post<Workshop>(this.url('/v1/workshops'), request);
  }

  /** `request.version` must be the version the person was editing; a stale one is refused. */
  update(id: string, request: WorkshopRequest): Observable<Workshop> {
    return this.http.put<Workshop>(this.url(`/v1/workshops/${encodeURIComponent(id)}`), request);
  }

  cancel(id: string, reason: string | null = null): Observable<unknown> {
    return this.http.post<unknown>(this.url(`/v1/workshops/${encodeURIComponent(id)}/cancel`), {
      reason,
    });
  }

  listLocations(): Observable<Location[]> {
    return this.http.get<Location[]>(this.url('/v1/locations'));
  }

  private url(path: string): string {
    return this.config.apiUrl(path);
  }
}

/** Builds the query string; `status` is repeated (`status=OPEN&status=FULL`), empty values are left out. */
export function searchParams(query: WorkshopSearchQuery): HttpParams {
  let params = new HttpParams();
  if (query.from) {
    params = params.set('from', query.from);
  }
  if (query.to) {
    params = params.set('to', query.to);
  }
  for (const status of query.statuses ?? []) {
    params = params.append('status', status);
  }
  if (query.locationId) {
    params = params.set('locationId', query.locationId);
  }
  if (query.hasSeats) {
    params = params.set('hasSeats', 'true');
  }
  const text = query.q?.trim();
  if (text) {
    params = params.set('q', text);
  }
  if (query.page !== undefined) {
    params = params.set('page', String(query.page));
  }
  if (query.size !== undefined) {
    params = params.set('size', String(query.size));
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  return params;
}
