import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigStore } from '@core/config/runtime-config';

import {
  CancelRequest,
  CancelResult,
  RegisterRequest,
  Registration,
  RegistrationStatus,
} from './registration.models';

@Injectable({ providedIn: 'root' })
export class RegistrationsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigStore);

  /** Everything that ever happened on one workshop, newest first. Optionally one status only. */
  history(workshopId: string, status?: RegistrationStatus | null): Observable<Registration[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Registration[]>(
      this.config.apiUrl(`/v1/workshops/${encodeURIComponent(workshopId)}/registrations`),
      { params },
    );
  }

  register(workshopId: string, request: RegisterRequest): Observable<Registration> {
    return this.http.post<Registration>(
      this.config.apiUrl(`/v1/workshops/${encodeURIComponent(workshopId)}/registrations`),
      request,
    );
  }

  cancel(registrationId: string, reason: string | null = null): Observable<CancelResult> {
    const body: CancelRequest = { reason };
    return this.http.post<CancelResult>(
      this.config.apiUrl(`/v1/registrations/${encodeURIComponent(registrationId)}/cancel`),
      body,
    );
  }
}
