import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideRuntimeConfig } from '@core/config/runtime-config';

import { StaffAccount } from './staff-accounts.model';
import { StaffAccountsService } from './staff-accounts.service';

const ACCOUNT: StaffAccount = {
  id: 'acc-1',
  email: 'sam@seatwise.local',
  fullName: 'Sam Patel',
  role: 'STAFF',
  active: true,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-02T09:00:00Z',
  version: 3,
};

describe('StaffAccountsService', () => {
  let service: StaffAccountsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRuntimeConfig({
          idpUrl: 'http://idp',
          realm: 'seatwise',
          clientId: 'seatwise-desk',
          apiBase: '/api',
        }),
      ],
    });
    service = TestBed.inject(StaffAccountsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists with role, active, page and size', () => {
    let result: unknown;
    service
      .list({ role: 'MANAGER', active: true, page: 2, size: 20 })
      .subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/v1/staff-accounts');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('role')).toBe('MANAGER');
    expect(req.request.params.get('active')).toBe('true');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    const page = { items: [ACCOUNT], page: 2, size: 20, totalItems: 41 };
    req.flush(page);

    expect(result).toEqual(page);
  });

  it('leaves out role and active when they are not filtered', () => {
    service.list({ role: null, active: null, page: 0, size: 20 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/staff-accounts');
    expect(req.request.params.has('role')).toBe(false);
    expect(req.request.params.has('active')).toBe(false);
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ items: [], page: 0, size: 20, totalItems: 0 });
  });

  it('can ask for deactivated accounts only', () => {
    service.list({ role: null, active: false, page: 0, size: 20 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/staff-accounts');
    expect(req.request.params.get('active')).toBe('false');
    req.flush({ items: [], page: 0, size: 20, totalItems: 0 });
  });

  it('gets one account', () => {
    let result: StaffAccount | undefined;
    service.get('acc-1').subscribe((a) => (result = a));

    const req = http.expectOne('/api/v1/staff-accounts/acc-1');
    expect(req.request.method).toBe('GET');
    req.flush(ACCOUNT);

    expect(result).toEqual(ACCOUNT);
  });

  it('creates an account', () => {
    const body = {
      email: 'sam@seatwise.local',
      fullName: 'Sam Patel',
      role: 'STAFF' as const,
      temporaryPassword: 'Kd7mQ2xRt9bW4c',
    };
    service.create(body).subscribe();

    const req = http.expectOne('/api/v1/staff-accounts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(ACCOUNT, { status: 201, statusText: 'Created' });
  });

  it('updates with PATCH and the version it was loaded at', () => {
    service.update('acc-1', { fullName: 'Sam P.', version: 3 }).subscribe();

    const req = http.expectOne('/api/v1/staff-accounts/acc-1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ fullName: 'Sam P.', version: 3 });
    req.flush({ ...ACCOUNT, fullName: 'Sam P.', version: 4 });
  });

  it('sets a new temporary password', () => {
    service.resetPassword('acc-1', 'Kd7mQ2xRt9bW4c').subscribe();

    const req = http.expectOne('/api/v1/staff-accounts/acc-1/password-reset');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ temporaryPassword: 'Kd7mQ2xRt9bW4c' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
