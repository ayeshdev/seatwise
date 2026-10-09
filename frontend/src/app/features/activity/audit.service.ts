import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigStore } from '@core/config/runtime-config';

import { AuditPage, AuditQuery } from './audit.models';

/** The one place the activity screens talk to the backend. Empty filters are left out of the query. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigStore);

  /** Newest first. The API narrows the result to what the signed-in role may see. */
  list(query: AuditQuery): Observable<AuditPage> {
    return this.http.get<AuditPage>(this.config.apiUrl('/v1/audit-events'), {
      params: auditParams(query),
    });
  }
}

export function auditParams(query: AuditQuery): HttpParams {
  let params = new HttpParams();
  const optional: [string, string | null | undefined][] = [
    ['entityType', query.entityType],
    ['entityId', query.entityId],
    ['workshopId', query.workshopId],
    ['from', query.from],
    ['to', query.to],
  ];
  for (const [key, value] of optional) {
    if (value) {
      params = params.set(key, value);
    }
  }
  return params.set('page', String(query.page)).set('size', String(query.size));
}
