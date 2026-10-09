import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideRuntimeConfig } from '@core/config/runtime-config';

import { AuthService } from './auth.service';
import { SessionStore } from './session.store';

const ME = { id: 'a1', email: 'admin@seatwise.local', fullName: 'Asha Rao', role: 'ADMIN' };

describe('SessionStore', () => {
  let store: SessionStore;
  let http: HttpTestingController;
  const auth = { login: jest.fn(), logout: jest.fn(async () => undefined) };

  beforeEach(() => {
    auth.logout.mockClear();
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
        { provide: AuthService, useValue: auth },
      ],
    });
    store = TestBed.inject(SessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts empty', () => {
    expect(store.me()).toBeNull();
    expect(store.role()).toBeNull();
    expect(store.isLoaded()).toBe(false);
    expect(store.loadError()).toBeNull();
  });

  it('loads the profile from /api/v1/me', async () => {
    const pending = store.load();
    http.expectOne('/api/v1/me').flush(ME);
    await pending;

    expect(store.me()).toEqual(ME);
    expect(store.role()).toBe('ADMIN');
    expect(store.isLoaded()).toBe(true);
    expect(store.loadError()).toBeNull();
    expect(store.isDeactivated()).toBe(false);
  });

  it('shares one request between callers', async () => {
    const first = store.load();
    const second = store.load();
    http.expectOne('/api/v1/me').flush(ME);

    await Promise.all([first, second]);
    expect(first).toBe(second);
  });

  it('records a deactivated account', async () => {
    const pending = store.load();
    http
      .expectOne('/api/v1/me')
      .flush(
        { status: 403, title: 'Forbidden', detail: 'inactive', code: 'ACCOUNT_INACTIVE' },
        { status: 403, statusText: 'Forbidden' },
      );
    await pending;

    expect(store.me()).toBeNull();
    expect(store.isLoaded()).toBe(true);
    expect(store.loadError()?.code).toBe('ACCOUNT_INACTIVE');
    expect(store.isDeactivated()).toBe(true);
  });

  it('records other failures without marking the account deactivated', async () => {
    const pending = store.load();
    http.expectOne('/api/v1/me').flush('boom', { status: 502, statusText: 'Bad Gateway' });
    await pending;

    expect(store.loadError()?.status).toBe(502);
    expect(store.isDeactivated()).toBe(false);
  });

  it('rejects a profile with an unknown role', async () => {
    const pending = store.load();
    http.expectOne('/api/v1/me').flush({ ...ME, role: 'OWNER' });
    await pending;

    expect(store.me()).toBeNull();
    expect(store.loadError()).not.toBeNull();
  });

  it('asks again on reload', async () => {
    const first = store.load();
    http.expectOne('/api/v1/me').flush('x', { status: 500, statusText: 'Server Error' });
    await first;

    const second = store.reload();
    http.expectOne('/api/v1/me').flush({ ...ME, role: 'STAFF' });
    await second;

    expect(store.role()).toBe('STAFF');
    expect(store.loadError()).toBeNull();
  });

  it('signs out through the auth service', async () => {
    await store.logout();

    expect(auth.logout).toHaveBeenCalledTimes(1);
  });
});
