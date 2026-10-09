import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigStore } from '@core/config/runtime-config';

import {
  CreateStaffAccount,
  StaffAccount,
  StaffAccountPage,
  StaffAccountQuery,
  UpdateStaffAccount,
} from './staff-accounts.model';

/** Thin typed wrapper over `/api/v1/staff-accounts` (Admin only). */
@Injectable({ providedIn: 'root' })
export class StaffAccountsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigStore);

  list(query: StaffAccountQuery): Observable<StaffAccountPage> {
    let params = new HttpParams();
    if (query.role !== null) {
      params = params.set('role', query.role);
    }
    if (query.active !== null) {
      params = params.set('active', String(query.active));
    }
    params = params.set('page', String(query.page)).set('size', String(query.size));
    return this.http.get<StaffAccountPage>(this.url(), { params });
  }

  get(id: string): Observable<StaffAccount> {
    return this.http.get<StaffAccount>(this.url(`/${encodeURIComponent(id)}`));
  }

  create(body: CreateStaffAccount): Observable<StaffAccount> {
    return this.http.post<StaffAccount>(this.url(), body);
  }

  update(id: string, body: UpdateStaffAccount): Observable<StaffAccount> {
    return this.http.patch<StaffAccount>(this.url(`/${encodeURIComponent(id)}`), body);
  }

  resetPassword(id: string, temporaryPassword: string): Observable<void> {
    return this.http.post<void>(this.url(`/${encodeURIComponent(id)}/password-reset`), {
      temporaryPassword,
    });
  }

  private url(suffix = ''): string {
    return this.config.apiUrl(`/v1/staff-accounts${suffix}`);
  }
}
