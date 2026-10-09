/**
 * Shared test data and providers for the workshop and registration specs. Test-only: nothing in the
 * app imports this file, and it uses no Jest globals so the app type-check stays clean.
 */
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider, WritableSignal, signal } from '@angular/core';

import { AuthService } from '@core/auth/auth.service';
import { Role } from '@core/auth/role';
import { SessionStore } from '@core/auth/session.store';
import { provideRuntimeConfig } from '@core/config/runtime-config';
import { problemInterceptor } from '@core/http/problem.interceptor';
import type { Registration } from '@features/registrations/registration.models';

import type { Workshop, WorkshopSummary } from './workshop.models';

export const API = '/api/v1';

const STAFF_REF = { id: 'staff-1', fullName: 'Amara Silva' };

export function makeWorkshop(overrides: Partial<Workshop> = {}): Workshop {
  return {
    id: 'w-1',
    code: 'POT-0412',
    title: 'Wheel-throwing for beginners',
    description: 'Bring an apron.',
    instructor: 'Amara Silva',
    location: { id: 'loc-1', name: 'Riverside' },
    startsAt: new Date(2030, 9, 17, 9, 30).toISOString(),
    endsAt: new Date(2030, 9, 17, 12, 0).toISOString(),
    capacity: 20,
    seatsTaken: 17,
    seatsLeft: 3,
    waitlistCount: 0,
    status: 'OPEN',
    version: 4,
    createdAt: '2030-01-01T00:00:00Z',
    createdBy: STAFF_REF,
    updatedAt: '2030-01-02T00:00:00Z',
    updatedBy: STAFF_REF,
    ...overrides,
  };
}

export function makeSummary(overrides: Partial<WorkshopSummary> = {}): WorkshopSummary {
  const { id, code, title, instructor, location, startsAt, endsAt, capacity, seatsTaken, seatsLeft, status } =
    makeWorkshop();
  return {
    id,
    code,
    title,
    instructor,
    location,
    startsAt,
    endsAt,
    capacity,
    seatsTaken,
    seatsLeft,
    status,
    ...overrides,
  };
}

export function makeRegistration(overrides: Partial<Registration> = {}): Registration {
  return {
    id: 'r-1',
    workshopId: 'w-1',
    attendeeName: 'Priya Shah',
    attendeeEmail: 'priya@example.com',
    status: 'ACTIVE',
    registeredAt: new Date(2030, 9, 1, 10, 15).toISOString(),
    registeredBy: STAFF_REF,
    promotedAt: null,
    waitlistPosition: null,
    cancelledAt: null,
    cancelledBy: null,
    cancellationReason: null,
    ...overrides,
  };
}

export interface FakeSession {
  role: WritableSignal<Role | null>;
}

/**
 * HttpClient with the real problem interceptor (so failures arrive as `ProblemError`, as in the
 * app), the testing backend, runtime config and a signed-in role.
 */
export function provideApiTesting(role: Role | null): {
  providers: (Provider | EnvironmentProviders)[];
  session: FakeSession;
} {
  const session: FakeSession = { role: signal(role) };
  return {
    session,
    providers: [
      provideHttpClient(withInterceptors([problemInterceptor])),
      provideHttpClientTesting(),
      provideRuntimeConfig({ idpUrl: 'http://idp', realm: 'r', clientId: 'c', apiBase: '/api' }),
      { provide: SessionStore, useValue: session },
      { provide: AuthService, useValue: { login: async () => undefined } },
    ],
  };
}

export const PROBLEM_HEADERS = { status: 409, statusText: 'Conflict' } as const;

export function problem(code: string, detail = 'Something specific', errors?: unknown): unknown {
  return { type: 'about:blank', title: code, status: 409, code, detail, ...(errors ? { errors } : {}) };
}
