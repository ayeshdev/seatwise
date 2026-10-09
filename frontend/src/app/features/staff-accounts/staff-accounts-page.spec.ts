import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { provideRuntimeConfig } from '@core/config/runtime-config';

import { StaffAccountsPage, paramsFromQuery, queryFromParams } from './staff-accounts-page';
import { StaffAccount } from './staff-accounts.model';

@Component({ template: '' })
class Blank {}

const SAM: StaffAccount = {
  id: 'acc-1',
  email: 'sam@seatwise.local',
  fullName: 'Sam Patel',
  role: 'STAFF',
  active: true,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T09:00:00Z',
  version: 1,
};
const ASHA: StaffAccount = {
  ...SAM,
  id: 'acc-2',
  email: 'asha@seatwise.local',
  fullName: 'Asha Rao',
  role: 'MANAGER',
  active: false,
};

describe('queryFromParams / paramsFromQuery', () => {
  it('defaults to active accounts, any role, first page', () => {
    expect(queryFromParams(convertToParamMap({}))).toEqual({
      role: null,
      active: true,
      page: 0,
      size: 20,
    });
  });

  it('reads role, deactivated and page', () => {
    expect(
      queryFromParams(convertToParamMap({ role: 'ADMIN', deactivated: 'true', page: '3' })),
    ).toEqual({ role: 'ADMIN', active: null, page: 3, size: 20 });
  });

  it('ignores junk', () => {
    expect(
      queryFromParams(convertToParamMap({ role: 'BOSS', deactivated: 'maybe', page: '-4' })),
    ).toEqual({ role: null, active: true, page: 0, size: 20 });
  });

  it('writes only what differs from the defaults', () => {
    expect(paramsFromQuery({ role: null, active: true, page: 0, size: 20 })).toEqual({
      role: null,
      deactivated: null,
      page: null,
    });
    expect(paramsFromQuery({ role: 'STAFF', active: null, page: 2, size: 20 })).toEqual({
      role: 'STAFF',
      deactivated: 'true',
      page: 2,
    });
  });
});

describe('StaffAccountsPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
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
        provideRouter([
          { path: 'staff-accounts', component: StaffAccountsPage },
          { path: 'staff-accounts/new', component: Blank },
          { path: 'staff-accounts/:id', component: Blank },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const listRequests = () => http.match((r) => r.url === '/api/v1/staff-accounts');
  const root = () => harness.routeNativeElement as HTMLElement;
  /** Lets navigation, effects and requests run, then renders the result. */
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
  };

  async function open(url: string, items: StaffAccount[] = [SAM, ASHA], totalItems = items.length) {
    await harness.navigateByUrl(url, StaffAccountsPage);
    const [req, ...rest] = listRequests();
    expect(rest).toHaveLength(0);
    req.flush({ items, page: 0, size: 20, totalItems });
    await settle();
    return req;
  }

  it('asks for active accounts by default and renders a row per account', async () => {
    const req = await open('/staff-accounts');

    expect(req.request.params.get('active')).toBe('true');
    expect(req.request.params.has('role')).toBe(false);
    expect(req.request.params.get('page')).toBe('0');
    expect(root().querySelector('h1')?.textContent).toContain('Staff accounts');

    const rows = Array.from(root().querySelectorAll('tbody tr'));
    expect(rows).toHaveLength(2);
    const first = rows[0].textContent ?? '';
    expect(first).toContain('Sam Patel');
    expect(first).toContain('sam@seatwise.local');
    expect(first).toContain('Front desk');
    expect(first).toContain('Active');
    const second = rows[1].textContent ?? '';
    expect(second).toContain('Programme manager');
    expect(second).toContain('Deactivated');
    expect(root().textContent).not.toContain('MANAGER');
  });

  it('turns the URL into filters, and the filters into the request', async () => {
    const req = await open('/staff-accounts?role=MANAGER&deactivated=true&page=1');

    expect(req.request.params.get('role')).toBe('MANAGER');
    expect(req.request.params.has('active')).toBe(false);
    expect(req.request.params.get('page')).toBe('1');
    const select = root().querySelector('select') as HTMLSelectElement;
    const toggle = root().querySelector('input[type=checkbox]') as HTMLInputElement;
    expect(select.value).toBe('MANAGER');
    expect(toggle.checked).toBe(true);
  });

  it('puts a changed role filter in the URL and reloads from the first page', async () => {
    await open('/staff-accounts?page=2');

    const select = root().querySelector('select') as HTMLSelectElement;
    select.value = 'STAFF';
    select.dispatchEvent(new Event('change'));
    await settle();

    expect(router.url).toBe('/staff-accounts?role=STAFF');
    const [req] = listRequests();
    expect(req.request.params.get('role')).toBe('STAFF');
    expect(req.request.params.get('active')).toBe('true');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ items: [SAM], page: 0, size: 20, totalItems: 1 });
  });

  it('shows deactivated accounts when the toggle is switched on', async () => {
    await open('/staff-accounts');

    const toggle = root().querySelector('input[type=checkbox]') as HTMLInputElement;
    toggle.click();
    await settle();

    expect(router.url).toBe('/staff-accounts?deactivated=true');
    const [req] = listRequests();
    expect(req.request.params.has('active')).toBe(false);
    req.flush({ items: [SAM, ASHA], page: 0, size: 20, totalItems: 2 });
  });

  it('moves between pages through the URL', async () => {
    await open('/staff-accounts', [SAM], 45);

    const next = Array.from(root().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Next',
    ) as HTMLButtonElement;
    next.click();
    await settle();

    expect(router.url).toBe('/staff-accounts?page=1');
    const [req] = listRequests();
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ items: [ASHA], page: 1, size: 20, totalItems: 45 });
  });

  it('opens the account when its row is clicked', async () => {
    await open('/staff-accounts');

    (root().querySelectorAll('tbody tr')[1] as HTMLElement).click();
    await settle();

    expect(router.url).toBe('/staff-accounts/acc-2');
  });

  it('opens the create form from the one primary button', async () => {
    await open('/staff-accounts');

    const add = Array.from(root().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Add staff member',
    ) as HTMLButtonElement;
    add.click();
    await settle();

    expect(router.url).toBe('/staff-accounts/new');
  });

  it('shows a spinner while loading', async () => {
    await harness.navigateByUrl('/staff-accounts', StaffAccountsPage);

    expect(root().textContent).toContain('Loading staff accounts');
    expect(root().querySelector('table')).toBeNull();
    listRequests().forEach((r) => r.flush({ items: [], page: 0, size: 20, totalItems: 0 }));
  });

  it('says so when nothing matches', async () => {
    await open('/staff-accounts?role=ADMIN', []);

    expect(root().textContent).toContain('No staff accounts found');
    expect(root().querySelector('table')).toBeNull();
  });

  it('explains a failed load in plain words and lets the person retry', async () => {
    await harness.navigateByUrl('/staff-accounts', StaffAccountsPage);
    const [req] = listRequests();
    req.flush({ code: 'FORBIDDEN', detail: 'nope' }, { status: 403, statusText: 'Forbidden' });
    await settle();

    expect(root().querySelector('[role=alert]')?.textContent).toContain(
      "You don't have permission to do that",
    );
    expect(root().textContent).not.toContain('FORBIDDEN');

    const retry = Array.from(root().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Try again',
    ) as HTMLButtonElement;
    retry.click();
    listRequests().forEach((r) => r.flush({ items: [SAM], page: 0, size: 20, totalItems: 1 }));
    await settle();
    expect(root().querySelectorAll('tbody tr')).toHaveLength(1);
  });
});
