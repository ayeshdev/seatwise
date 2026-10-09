import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { provideApiTesting } from '@features/workshops/workshop.fixtures';

import { AccountActivityPage } from './account-activity-page';
import { AUDIT_URL, makeEvent, makePage } from './audit.fixtures';

describe('AccountActivityPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let root: HTMLElement;

  const isAudit = (r: HttpRequest<unknown>) => r.url === AUDIT_URL;

  async function open(url = '/account-activity'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideApiTesting('ADMIN').providers,
        provideRouter([{ path: 'account-activity', component: AccountActivityPage }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, AccountActivityPage);
    root = harness.routeNativeElement as HTMLElement;
    harness.detectChanges();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function answer(body = makePage([accountEvent()])) {
    const req = http.expectOne(isAudit);
    req.flush(body);
    await settle();
    return req;
  }

  function accountEvent(id = 1, entityId = 'acc-9') {
    return makeEvent({
      id,
      entityType: 'STAFF_ACCOUNT',
      entityId,
      workshopId: null,
      action: 'ROLE_CHANGED',
      summary: 'Changed Sam Patel from Staff to Manager',
      changes: { role: { from: 'STAFF', to: 'MANAGER' } },
    });
  }

  afterEach(() => http.verify());

  it('shows account changes only', async () => {
    await open();
    const req = await answer();

    expect(root.querySelector('h1')?.textContent).toContain('Account activity');
    expect(req.request.params.get('entityType')).toBe('STAFF_ACCOUNT');
    expect(req.request.params.has('entityId')).toBe(false);
    expect(root.textContent).toContain('Changed Sam Patel from Staff to Manager');
  });

  it('links each entry to the staff account', async () => {
    await open();
    await answer(makePage([accountEvent(2, 'acc-9'), accountEvent(1, 'acc-4')]));

    const links = Array.from(root.querySelectorAll('li a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/staff-accounts/acc-9', '/staff-accounts/acc-4']);
    expect(links[0].textContent?.trim()).toBe('View account');
  });

  it('narrows by date and keeps that in the address bar', async () => {
    await open('/account-activity?from=2030-10-01');
    const first = await answer();
    expect(first.request.params.get('from')).toBe('2030-10-01');

    const to = root.querySelector('#sw-activity-to') as HTMLInputElement;
    to.value = '2030-10-31';
    to.dispatchEvent(new Event('input'));
    await settle();

    const second = await answer();
    expect(second.request.params.get('entityType')).toBe('STAFF_ACCOUNT');
    expect(second.request.params.get('from')).toBe('2030-10-01');
    expect(second.request.params.get('to')).toBe('2030-10-31');
    expect(router.url).toBe('/account-activity?from=2030-10-01&to=2030-10-31');
  });

  it('has no booking or workshop filters', async () => {
    await open();
    await answer();

    const buttons = Array.from(root.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).not.toContain('Bookings');
    expect(buttons).not.toContain('Workshop changes');
  });
});
