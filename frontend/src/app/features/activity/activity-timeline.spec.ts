import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { formatDateTime } from '@features/workshops/workshop-format';
import { provideApiTesting } from '@features/workshops/workshop.fixtures';

import { ActivityTimeline, LOAD_ERROR_MESSAGE } from './activity-timeline';
import { AUDIT_URL, makeEvent, makePage } from './audit.fixtures';

describe('ActivityTimeline', () => {
  let fixture: ComponentFixture<ActivityTimeline>;
  let http: HttpTestingController;

  const isAudit = (r: HttpRequest<unknown>) => r.url === AUDIT_URL;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = () => fixture.nativeElement as HTMLElement;

  function setup(inputs: Record<string, unknown> = {}): void {
    TestBed.configureTestingModule({
      providers: [...provideApiTesting('MANAGER').providers, provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActivityTimeline);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  async function answer(body: unknown): Promise<TestRequest> {
    const req = http.expectOne(isAudit);
    req.flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
    return req;
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  afterEach(() => http.verify());

  describe('what it asks for', () => {
    it('asks for one workshop and its bookings', async () => {
      setup({ workshopId: 'w-1' });
      const req = await answer(makePage([]));

      expect(req.request.params.get('workshopId')).toBe('w-1');
      expect(req.request.params.has('entityType')).toBe(false);
      expect(req.request.params.get('page')).toBe('0');
    });

    it('asks for one record', async () => {
      setup({ entityType: 'STAFF_ACCOUNT', entityId: 'acc-1' });
      const req = await answer(makePage([]));

      expect(req.request.params.get('entityType')).toBe('STAFF_ACCOUNT');
      expect(req.request.params.get('entityId')).toBe('acc-1');
    });

    it('asks for everything of one kind, within the dates', async () => {
      setup({ entityType: 'REGISTRATION', from: '2030-10-01', to: '2030-10-07' });
      const req = await answer(makePage([]));

      expect(req.request.params.get('entityType')).toBe('REGISTRATION');
      expect(req.request.params.has('entityId')).toBe(false);
      expect(req.request.params.get('from')).toBe('2030-10-01');
      expect(req.request.params.get('to')).toBe('2030-10-07');
    });

    it('starts again from the first page when an input changes', async () => {
      setup({ entityType: 'WORKSHOP' });
      await answer(makePage([makeEvent()]));

      fixture.componentRef.setInput('entityType', 'REGISTRATION');
      fixture.detectChanges();
      const req = http.expectOne(isAudit);
      expect(req.request.params.get('entityType')).toBe('REGISTRATION');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(makePage([makeEvent({ id: 2, summary: 'Registered Priya' })]));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain('Registered Priya');
      expect(text()).not.toContain('Changed capacity');
    });
  });

  describe('entries', () => {
    it('shows the summary, who did it, and when', async () => {
      const event = makeEvent();
      setup({ workshopId: 'w-1' });
      await answer(makePage([event]));

      expect(text()).toContain('Changed capacity from 12 to 16');
      expect(text()).toContain('Amara Silva');
      expect(text()).toContain(formatDateTime(event.occurredAt));
      expect(text()).toContain('Edited');
      expect(el().querySelector('time')?.getAttribute('datetime')).toBe(event.occurredAt);
    });

    it('calls the actor "System" when there is none', async () => {
      setup({ entityType: 'STAFF_ACCOUNT' });
      await answer(
        makePage([makeEvent({ actor: null, action: 'CREATED', summary: 'Created the first admin', changes: {} })]),
      );

      expect(text()).toContain('System');
      expect(text()).toContain('Created the first admin');
    });

    it('keeps the order the API sent (newest first)', async () => {
      setup({ workshopId: 'w-1' });
      await answer(
        makePage([
          makeEvent({ id: 3, summary: 'Third' }),
          makeEvent({ id: 2, summary: 'Second' }),
          makeEvent({ id: 1, summary: 'First' }),
        ]),
      );

      const summaries = Array.from(el().querySelectorAll('li p:nth-of-type(2)')).map((p) => p.textContent?.trim());
      expect(summaries).toEqual(['Third', 'Second', 'First']);
    });

    it('always names the kind of change in words, not just colour', async () => {
      setup({ workshopId: 'w-1' });
      await answer(
        makePage([
          makeEvent({ id: 3, action: 'PROMOTED', summary: 'Priya got a seat', changes: {} }),
          makeEvent({ id: 2, action: 'CANCELLED', summary: 'Cancelled the workshop', changes: {} }),
        ]),
      );

      expect(text()).toContain('Moved off the waitlist');
      expect(text()).toContain('Cancelled');
      const dots = Array.from(el().querySelectorAll('li > span[aria-hidden="true"]:not(.absolute)'));
      expect(dots[0].className).toContain('bg-ok');
      expect(dots[1].className).toContain('bg-full');
    });
  });

  describe('details', () => {
    it('is hidden until asked for, then lists each change in plain words', async () => {
      setup({ workshopId: 'w-1' });
      await answer(
        makePage([
          makeEvent({
            changes: {
              capacity: { from: 12, to: 16 },
              startsAt: {
                from: new Date(2030, 9, 17, 9, 30).toISOString(),
                to: new Date(2030, 9, 18, 10, 0).toISOString(),
              },
              description: { from: null, to: 'Bring an apron' },
            },
          }),
        ]),
      );

      expect(el().querySelector('dl')).toBeNull();
      const toggle = button('Details');
      expect(toggle?.getAttribute('aria-expanded')).toBe('false');

      toggle?.click();
      fixture.detectChanges();

      const rows = Array.from(el().querySelectorAll('dl > dt')).map((dt) => dt.textContent?.trim());
      expect(rows).toEqual(['Capacity', 'Starts', 'Description']);
      const details = el().querySelector('dl')?.textContent ?? '';
      expect(details).toContain('12');
      expect(details).toContain('16');
      expect(details).toContain(formatDateTime(new Date(2030, 9, 18, 10, 0).toISOString()));
      expect(details).toContain('Not set');
      expect(details).toContain('Bring an apron');
      expect(details).not.toContain('T10:00');
      expect(button('Hide details')?.getAttribute('aria-expanded')).toBe('true');

      button('Hide details')?.click();
      fixture.detectChanges();
      expect(el().querySelector('dl')).toBeNull();
    });

    it('formats an account being switched off as words', async () => {
      setup({ entityType: 'STAFF_ACCOUNT' });
      await answer(
        makePage([
          makeEvent({
            entityType: 'STAFF_ACCOUNT',
            workshopId: null,
            action: 'DEACTIVATED',
            summary: 'Deactivated Sam Patel',
            changes: { active: { from: true, to: false }, role: { from: 'STAFF', to: 'MANAGER' } },
          }),
        ]),
      );

      button('Details')?.click();
      fixture.detectChanges();

      const details = el().querySelector('dl')?.textContent ?? '';
      expect(details).toContain('Account status');
      expect(details).toContain('Active');
      expect(details).toContain('Inactive');
      expect(details).toContain('Manager');
      expect(details).not.toMatch(/true|false|MANAGER/);
    });

    it('offers no "Details" when nothing changed', async () => {
      setup({ workshopId: 'w-1' });
      await answer(makePage([makeEvent({ changes: {} })]));

      expect(button('Details')).toBeUndefined();
    });
  });

  describe('paging', () => {
    it('appends the next page when "Load more" is pressed, then stops when everything is shown', async () => {
      setup({ workshopId: 'w-1', pageSize: 2 });
      const first = await answer(
        makePage([makeEvent({ id: 4, summary: 'Fourth' }), makeEvent({ id: 3, summary: 'Third' })], {
          size: 2,
          totalItems: 3,
        }),
      );
      expect(first.request.params.get('size')).toBe('2');
      expect(text()).toContain('Fourth');

      button('Load more')?.click();
      fixture.detectChanges();
      const second = http.expectOne(isAudit);
      expect(second.request.params.get('page')).toBe('1');
      second.flush(makePage([makeEvent({ id: 2, summary: 'Second' })], { page: 1, size: 2, totalItems: 3 }));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain('Fourth');
      expect(text()).toContain('Third');
      expect(text()).toContain('Second');
      expect(button('Load more')).toBeUndefined();
    });

    it('has no "Load more" when everything fits on one page', async () => {
      setup({ workshopId: 'w-1' });
      await answer(makePage([makeEvent()]));

      expect(button('Load more')).toBeUndefined();
    });

    it('shows the first page only when paging is off', async () => {
      setup({ workshopId: 'w-1', paged: false, pageSize: 1 });
      await answer(makePage([makeEvent()], { size: 1, totalItems: 5 }));

      expect(button('Load more')).toBeUndefined();
    });
  });

  describe('empty, loading and failing', () => {
    it('says so when there is nothing yet', async () => {
      setup({ workshopId: 'w-1' });
      await answer(makePage([]));

      expect(text()).toContain('No activity yet.');
    });

    it('shows it is loading before the answer arrives, and not "No activity yet."', () => {
      setup({ workshopId: 'w-1' });

      expect(el().querySelector('[role="status"]')).not.toBeNull();
      expect(text()).not.toContain('No activity yet.');
      http.expectOne(isAudit).flush(makePage([]));
    });

    it('explains a failure in plain words and retries from the same place', async () => {
      setup({ workshopId: 'w-1' });
      http.expectOne(isAudit).flush({}, { status: 503, statusText: 'Service Unavailable' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain(LOAD_ERROR_MESSAGE);
      expect(text()).not.toContain('503');

      button('Try again')?.click();
      fixture.detectChanges();
      http.expectOne(isAudit).flush(makePage([makeEvent({ summary: 'Back again' })]));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain('Back again');
      expect(text()).not.toContain(LOAD_ERROR_MESSAGE);
    });

    it('keeps what is on screen when a later page fails, and retries that page', async () => {
      setup({ workshopId: 'w-1', pageSize: 1 });
      await answer(makePage([makeEvent({ summary: 'Kept' })], { size: 1, totalItems: 2 }));

      button('Load more')?.click();
      fixture.detectChanges();
      http.expectOne(isAudit).flush({}, { status: 503, statusText: 'Service Unavailable' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain('Kept');
      expect(text()).toContain(LOAD_ERROR_MESSAGE);

      button('Try again')?.click();
      fixture.detectChanges();
      const retry = http.expectOne(isAudit);
      expect(retry.request.params.get('page')).toBe('1');
      retry.flush(makePage([makeEvent({ id: 2, summary: 'Second' })], { page: 1, size: 1, totalItems: 2 }));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text()).toContain('Second');
    });
  });

  describe('links', () => {
    it('links accounts and workshops only when asked to', async () => {
      setup({ entityType: 'STAFF_ACCOUNT' });
      await answer(
        makePage([makeEvent({ entityType: 'STAFF_ACCOUNT', entityId: 'acc-9', workshopId: null })]),
      );
      expect(el().querySelector('a')).toBeNull();

      fixture.componentRef.setInput('showLinks', true);
      fixture.detectChanges();
      const link = el().querySelector('a');
      expect(link?.textContent?.trim()).toBe('View account');
      expect(link?.getAttribute('href')).toBe('/staff-accounts/acc-9');
    });

    it('links a booking to its workshop', async () => {
      setup({ entityType: 'REGISTRATION', showLinks: true });
      await answer(
        makePage([makeEvent({ entityType: 'REGISTRATION', entityId: 'r-1', workshopId: 'w-7' })]),
      );

      const link = el().querySelector('a');
      expect(link?.textContent?.trim()).toBe('View workshop');
      expect(link?.getAttribute('href')).toBe('/workshops/w-7');
    });
  });
});
