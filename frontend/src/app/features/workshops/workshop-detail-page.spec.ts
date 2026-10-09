import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Role } from '@core/auth/role';
import { ToastService } from '@core/layout/toast.service';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';

import { POLL_INTERVAL_MS, WorkshopDetailPage } from './workshop-detail-page';
import { formatWhen } from './workshop-format';
import { API, makeRegistration, makeWorkshop, provideApiTesting } from './workshop.fixtures';
import { Workshop } from './workshop.models';

@Component({ selector: 'sw-stub', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Stub {}

/**
 * Fakes only setInterval/clearInterval, which is all the poller uses. Faking setTimeout and
 * friends as well would leave Angular's own scheduling (and `whenStable`) waiting forever.
 */
function useFakeIntervals(): void {
  jest.useFakeTimers({
    doNotFake: [
      'Date',
      'hrtime',
      'nextTick',
      'performance',
      'queueMicrotask',
      'requestAnimationFrame',
      'cancelAnimationFrame',
      'requestIdleCallback',
      'cancelIdleCallback',
      'setImmediate',
      'clearImmediate',
      'setTimeout',
      'clearTimeout',
    ],
  });
}

describe('WorkshopDetailPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let root: HTMLElement;
  let visibility: DocumentVisibilityState;

  const isWorkshop = (r: HttpRequest<unknown>) => r.url === `${API}/workshops/w-1`;
  const isHistory = (r: HttpRequest<unknown>) => r.url === `${API}/workshops/w-1/registrations` && r.method === 'GET';

  async function open(role: Role, workshop: Workshop = makeWorkshop()): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideApiTesting(role).providers,
        provideRouter([
          { path: 'workshops/:id/edit', component: Stub },
          { path: 'workshops/:id', component: WorkshopDetailPage },
          { path: 'workshops', component: Stub },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/workshops/w-1', WorkshopDetailPage);
    root = harness.routeNativeElement as HTMLElement;
    harness.detectChanges();
    http.expectOne(isWorkshop).flush(workshop);
    await settle();
    http.expectOne(isHistory).flush([makeRegistration()]);
    await settle();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  function setVisibility(state: DocumentVisibilityState): void {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
  }

  beforeEach(() => {
    visibility = 'visible';
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => visibility,
    });
  });

  afterEach(() => {
    http.verify();
    jest.useRealTimers();
    Reflect.deleteProperty(document, 'visibilityState');
  });

  describe('what it shows', () => {
    it('shows the header, facts, seats and waitlist in plain words', async () => {
      const w = makeWorkshop({ waitlistCount: 2, seatsLeft: 3, status: 'OPEN' });
      await open('STAFF', w);

      expect(root.querySelector('h1')?.textContent).toContain('Wheel-throwing for beginners');
      expect(root.textContent).toContain('POT-0412');
      expect(root.textContent).toContain('Open');
      expect(root.textContent).toContain(formatWhen(w.startsAt, w.endsAt));
      expect(root.textContent).toContain('Riverside');
      expect(root.textContent).toContain('Amara Silva');
      expect(root.textContent).toContain('Bring an apron.');
      expect(root.textContent).toContain('3 of 20 left');
      expect(root.textContent).toContain('2 people on the waitlist.');
      expect(root.textContent).not.toContain('w-1');
    });

    it('explains a cancelled workshop and hides the register panel', async () => {
      await open('STAFF', makeWorkshop({ status: 'CANCELLED', seatsLeft: 0 }));

      expect(root.textContent).toContain('This workshop is cancelled.');
      expect(root.textContent).toContain('Existing registrations are kept');
      expect(root.textContent).not.toContain('Register an attendee');
    });

    it('says so when the workshop does not exist', async () => {
      TestBed.configureTestingModule({
        providers: [
          ...provideApiTesting('STAFF').providers,
          provideRouter([{ path: 'workshops/:id', component: WorkshopDetailPage }]),
        ],
      });
      http = TestBed.inject(HttpTestingController);
      harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/workshops/w-1', WorkshopDetailPage);
      root = harness.routeNativeElement as HTMLElement;
      harness.detectChanges();
      http
        .expectOne(isWorkshop)
        .flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
      await settle();

      expect(root.textContent).toContain("We couldn't find that workshop");
    });
  });

  describe('role-aware actions', () => {
    it('gives a manager Edit and Cancel workshop', async () => {
      await open('MANAGER');

      expect(button('Edit')).toBeDefined();
      expect(button('Cancel workshop')).toBeDefined();
    });

    it('does not show them to staff', async () => {
      await open('STAFF');

      expect(button('Edit')).toBeUndefined();
      expect(button('Cancel workshop')).toBeUndefined();
    });

    it('opens the edit form', async () => {
      await open('MANAGER');

      button('Edit')?.click();
      await settle();

      expect(router.url).toBe('/workshops/w-1/edit');
    });
  });

  describe('cancelling the workshop', () => {
    it('asks first, explains what happens, then cancels with the reason', async () => {
      await open('MANAGER');
      const confirm = jest
        .spyOn(TestBed.inject(ConfirmDialogService), 'confirm')
        .mockResolvedValue({ confirmed: true, reason: 'Instructor unwell' });
      const toasts = jest.spyOn(TestBed.inject(ToastService), 'success');

      button('Cancel workshop')?.click();
      await settle();

      expect(confirm).toHaveBeenCalledWith(
        expect.objectContaining({
          danger: true,
          askReason: true,
          message: 'Existing registrations are kept; no new bookings will be accepted.',
        }),
      );
      const post = http.expectOne(`${API}/workshops/w-1/cancel`);
      expect(post.request.body).toEqual({ reason: 'Instructor unwell' });
      post.flush(makeWorkshop({ status: 'CANCELLED' }));
      expect(toasts).toHaveBeenCalledWith('The workshop is cancelled.');

      http.expectOne(isWorkshop).flush(makeWorkshop({ status: 'CANCELLED', seatsLeft: 0 }));
      await settle();
      http.expectOne(isHistory).flush([]);
      await settle();
      expect(root.textContent).toContain('This workshop is cancelled.');
    });

    it('does nothing when the manager changes their mind', async () => {
      await open('MANAGER');
      jest
        .spyOn(TestBed.inject(ConfirmDialogService), 'confirm')
        .mockResolvedValue({ confirmed: false, reason: null });

      button('Cancel workshop')?.click();
      await settle();

      http.expectNone(`${API}/workshops/w-1/cancel`);
    });
  });

  describe('keeping seat counts fresh', () => {
    it('re-reads the workshop every 15 seconds while the page is visible', async () => {
      useFakeIntervals();
      await open('STAFF');

      jest.advanceTimersByTime(POLL_INTERVAL_MS - 1);
      http.expectNone(isWorkshop);

      jest.advanceTimersByTime(1);
      http.expectOne(isWorkshop).flush(makeWorkshop({ seatsTaken: 18, seatsLeft: 2 }));
      await settle();
      expect(root.textContent).toContain('2 of 20 left');
      // The counts moved, so the bookings table reloads too.
      http.expectOne(isHistory).flush([makeRegistration()]);

      jest.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne(isWorkshop).flush(makeWorkshop({ seatsTaken: 18, seatsLeft: 2 }));
      await settle();
      http.expectNone(isHistory);
    });

    it('stops while the page is hidden and catches up when it is shown again', async () => {
      useFakeIntervals();
      await open('STAFF');

      setVisibility('hidden');
      jest.advanceTimersByTime(POLL_INTERVAL_MS * 4);
      http.expectNone(isWorkshop);

      setVisibility('visible');
      http.expectOne(isWorkshop).flush(makeWorkshop());
      jest.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne(isWorkshop).flush(makeWorkshop());
    });

    it('stops for good when the page is left', async () => {
      useFakeIntervals();
      await open('STAFF');

      await router.navigateByUrl('/workshops');
      jest.advanceTimersByTime(POLL_INTERVAL_MS * 4);
      http.expectNone(isWorkshop);

      setVisibility('hidden');
      setVisibility('visible');
      http.expectNone(isWorkshop);
    });

    it('does not start polling when the page opens in a hidden tab', async () => {
      visibility = 'hidden';
      useFakeIntervals();
      await open('STAFF');

      jest.advanceTimersByTime(POLL_INTERVAL_MS * 3);
      http.expectNone(isWorkshop);
    });
  });
});
