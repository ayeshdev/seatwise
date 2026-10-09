import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { RuntimeConfigStore } from '@core/config/runtime-config';
import { ProblemError, isProblemError, toProblemError } from '@core/http/problem';

import { AuthService } from './auth.service';
import { Role, isRole } from './role';

export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

function isMe(value: unknown): value is Me {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const r = value as Record<string, unknown>;
  return (
    typeof r['id'] === 'string' &&
    typeof r['email'] === 'string' &&
    typeof r['fullName'] === 'string' &&
    isRole(r['role'])
  );
}

/** The signed-in staff member, as the API sees them (`GET /api/v1/me`). */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigStore);
  private readonly auth = inject(AuthService);

  private readonly meState = signal<Me | null>(null);
  private readonly errorState = signal<ProblemError | null>(null);
  private readonly loadedState = signal(false);
  private pending: Promise<void> | null = null;

  readonly me = this.meState.asReadonly();
  readonly role = computed(() => this.meState()?.role ?? null);
  /** True once the first `/me` attempt has finished, successfully or not. */
  readonly isLoaded = this.loadedState.asReadonly();
  readonly loadError = this.errorState.asReadonly();
  readonly isDeactivated = computed(() => this.errorState()?.code === 'ACCOUNT_INACTIVE');

  /** Loads the profile once; later calls share the same attempt. Never rejects. */
  load(): Promise<void> {
    this.pending ??= this.fetchMe();
    return this.pending;
  }

  /** Forgets the cached result and asks the API again. */
  reload(): Promise<void> {
    this.pending = null;
    return this.load();
  }

  /** Called when any later request reveals the account has been switched off. */
  markDeactivated(): void {
    this.meState.set(null);
    this.errorState.set(new ProblemError(403, 'ACCOUNT_INACTIVE', null));
    this.loadedState.set(true);
  }

  logout(): Promise<void> {
    return this.auth.logout();
  }

  private asProblem(error: unknown): ProblemError {
    if (isProblemError(error)) {
      return error;
    }
    if (error instanceof HttpErrorResponse) {
      return toProblemError(error);
    }
    return new ProblemError(0, 'UNKNOWN', null);
  }

  private async fetchMe(): Promise<void> {
    try {
      const body: unknown = await firstValueFrom(this.http.get<unknown>(this.config.apiUrl('/v1/me')));
      if (!isMe(body)) {
        throw new ProblemError(200, 'UNKNOWN', 'Unexpected /me response');
      }
      this.meState.set(body);
      this.errorState.set(null);
    } catch (error) {
      this.meState.set(null);
      this.errorState.set(this.asProblem(error));
    } finally {
      this.loadedState.set(true);
    }
  }
}
