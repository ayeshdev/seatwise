import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { provideApiTesting } from '@features/workshops/workshop.fixtures';

import { ActivityPage } from './activity-page';
import { AUDIT_URL, makeEvent, makePage } from './audit.fixtures';

describe('ActivityPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let root: HTMLElement;

  const isAudit = (r: HttpRequest<unknown>) => r.url === AUDIT_URL;

  async function open(url = '/activity'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideApiTesting('MANAGER').providers,
        provideRouter([{ path: 'activity', component: ActivityPage }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ActivityPage);
    root = harness.routeNativeElement as HTMLElement;
    harness.detectChanges();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function answer(body = makePage([makeEvent()])) {
    const req = http.expectOne(isAudit);
    req.flush(body);
    await settle();
    return req;
  }

  function chip(label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
    if (!found) {
      throw new Error(`No button "${label}"`);
    }
    return found;
  }

  function dateInput(id: string): HTMLInputElement {
    return root.querySelector(`#${id}`) as HTMLInputElement;
  }

  function setDate(id: string, value: string): void {
    const input = dateInput(id);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  afterEach(() => http.verify());

  it('opens on all activity, with the heading and the timeline', async () => {
    await open();
    const req = await answer();

    expect(root.querySelector('h1')?.textContent).toContain('Activity');
    expect(req.request.params.has('entityType')).toBe(false);
    expect(req.request.params.has('from')).toBe(false);
    expect(chip('All activity').getAttribute('aria-pressed')).toBe('true');
    expect(root.textContent).toContain('Changed capacity from 12 to 16');
  });

  it('filters to workshop changes and writes that in the address bar', async () => {
    await open();
    await answer();

    chip('Workshop changes').click();
    await settle();

    const req = await answer();
    expect(req.request.params.get('entityType')).toBe('WORKSHOP');
    expect(router.url).toBe('/activity?show=workshops');
    expect(chip('Workshop changes').getAttribute('aria-pressed')).toBe('true');
    expect(chip('All activity').getAttribute('aria-pressed')).toBe('false');
  });

  it('filters to bookings', async () => {
    await open();
    await answer();

    chip('Bookings').click();
    await settle();

    const req = await answer();
    expect(req.request.params.get('entityType')).toBe('REGISTRATION');
    expect(router.url).toBe('/activity?show=bookings');
  });

  it('reads the filters from the address bar', async () => {
    await open('/activity?show=bookings&from=2030-10-01&to=2030-10-07');
    const req = await answer();

    expect(req.request.params.get('entityType')).toBe('REGISTRATION');
    expect(req.request.params.get('from')).toBe('2030-10-01');
    expect(req.request.params.get('to')).toBe('2030-10-07');
    expect(chip('Bookings').getAttribute('aria-pressed')).toBe('true');
    expect(dateInput('sw-activity-from').value).toBe('2030-10-01');
    expect(dateInput('sw-activity-to').value).toBe('2030-10-07');
  });

  it('ignores a nonsense address bar', async () => {
    await open('/activity?show=everything&from=yesterday');
    const req = await answer();

    expect(req.request.params.has('entityType')).toBe(false);
    expect(req.request.params.has('from')).toBe(false);
  });

  it('puts a chosen date range in the address bar and asks for it', async () => {
    await open();
    await answer();

    setDate('sw-activity-from', '2030-10-01');
    await settle();
    const first = await answer();
    expect(first.request.params.get('from')).toBe('2030-10-01');

    setDate('sw-activity-to', '2030-10-07');
    await settle();
    const second = await answer();
    expect(second.request.params.get('from')).toBe('2030-10-01');
    expect(second.request.params.get('to')).toBe('2030-10-07');
    expect(router.url).toBe('/activity?from=2030-10-01&to=2030-10-07');
  });

  it('keeps the kind of activity when the dates change', async () => {
    await open('/activity?show=workshops');
    await answer();

    setDate('sw-activity-from', '2030-10-01');
    await settle();

    const req = await answer();
    expect(req.request.params.get('entityType')).toBe('WORKSHOP');
    expect(router.url).toBe('/activity?show=workshops&from=2030-10-01');
  });

  it('drags the other end along rather than making an empty range', async () => {
    await open('/activity?from=2030-10-01&to=2030-10-07');
    await answer();

    setDate('sw-activity-from', '2030-10-20');
    await settle();

    const req = await answer();
    expect(req.request.params.get('from')).toBe('2030-10-20');
    expect(req.request.params.get('to')).toBe('2030-10-20');
  });

  it('clears the dates', async () => {
    await open('/activity?from=2030-10-01&to=2030-10-07');
    await answer();

    chip('Clear dates').click();
    await settle();

    const req = await answer();
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('to')).toBe(false);
    expect(router.url).toBe('/activity');
  });

  it('follows the browser back button', async () => {
    await open();
    await answer();
    await router.navigateByUrl('/activity?show=bookings');
    await settle();
    const bookings = await answer();
    expect(bookings.request.params.get('entityType')).toBe('REGISTRATION');

    await router.navigateByUrl('/activity');
    await settle();
    const all = await answer();

    expect(all.request.params.has('entityType')).toBe(false);
    expect(chip('All activity').getAttribute('aria-pressed')).toBe('true');
  });

  it('links entries to the workshop they are about', async () => {
    await open();
    await answer(makePage([makeEvent({ workshopId: 'w-7' })]));

    const link = root.querySelector('a[href="/workshops/w-7"]');
    expect(link?.textContent?.trim()).toBe('View workshop');
  });
});
