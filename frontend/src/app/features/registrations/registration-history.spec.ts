import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Role } from '@core/auth/role';
import { ToastService } from '@core/layout/toast.service';
import {
  API,
  PROBLEM_HEADERS,
  makeRegistration,
  problem,
  provideApiTesting,
} from '@features/workshops/workshop.fixtures';
import { formatDateTime } from '@features/workshops/workshop-format';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';

import { Registration } from './registration.models';
import { ALREADY_CANCELLED_MESSAGE, RegistrationHistory } from './registration-history';

describe('RegistrationHistory', () => {
  let fixture: ComponentFixture<RegistrationHistory>;
  let http: HttpTestingController;
  let toasts: ToastService;
  let confirm: jest.SpyInstance;
  let changed: jest.Mock;
  let el: HTMLElement;

  const isHistory = (r: HttpRequest<unknown>) =>
    r.url === `${API}/workshops/w-1/registrations` && r.method === 'GET';

  async function create(rows: Registration[], role: Role = 'STAFF'): Promise<void> {
    TestBed.configureTestingModule({ providers: provideApiTesting(role).providers });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    jest.spyOn(toasts, 'success');
    jest.spyOn(toasts, 'show');
    confirm = jest.spyOn(TestBed.inject(ConfirmDialogService), 'confirm');
    fixture = TestBed.createComponent(RegistrationHistory);
    fixture.componentRef.setInput('workshopId', 'w-1');
    changed = jest.fn();
    fixture.componentInstance.changed.subscribe(changed);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne(isHistory).flush(rows);
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement {
    const found = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    );
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found;
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  describe('the table', () => {
    it('shows who, when and what happened for every kind of booking', async () => {
      const cancelledAt = new Date(2030, 9, 3, 16, 45).toISOString();
      await create([
        makeRegistration({
          id: 'r-3',
          attendeeName: 'Lena Ortiz',
          status: 'CANCELLED',
          cancelledAt,
          cancelledBy: { id: 's-2', fullName: 'Noor Khan' },
          cancellationReason: 'Moved abroad',
        }),
        makeRegistration({
          id: 'r-2',
          attendeeName: 'Sam Lee',
          attendeeEmail: 'sam@example.com',
          status: 'ACTIVE',
          promotedAt: new Date(2030, 9, 3, 16, 46).toISOString(),
        }),
        makeRegistration({ id: 'r-1', status: 'WAITLISTED', waitlistPosition: 2 }),
      ]);

      const rows = el.querySelectorAll('tbody tr');
      expect(rows).toHaveLength(3);

      expect(rows[0].textContent).toContain('Lena Ortiz');
      expect(rows[0].textContent).toContain('Cancelled');
      expect(rows[0].textContent).toContain(`Cancelled by Noor Khan · ${formatDateTime(cancelledAt)}`);
      expect(rows[0].textContent).toContain('— Moved abroad');
      expect(rows[0].textContent).toContain('Registered by Amara Silva');
      expect(rows[0].querySelector('button')).toBeNull();

      expect(rows[1].textContent).toContain('sam@example.com');
      expect(rows[1].textContent).toContain('Registered');
      expect(rows[1].textContent).toContain('Promoted from waitlist — call to confirm');

      expect(rows[2].textContent).toContain('Waitlisted');
      expect(rows[2].textContent).toContain('Waitlist position 2');
      expect(rows[2].textContent).not.toContain('Promoted');
      expect(el.textContent).not.toContain('2030-');
    });

    it('only flags a promotion on rows that are still registered', async () => {
      await create([
        makeRegistration({
          status: 'CANCELLED',
          promotedAt: new Date(2030, 9, 3).toISOString(),
          cancelledAt: new Date(2030, 9, 4).toISOString(),
          cancelledBy: { id: 's-2', fullName: 'Noor Khan' },
        }),
      ]);

      expect(el.textContent).not.toContain('Promoted from waitlist');
    });

    it('says so when there is nothing to show', async () => {
      await create([]);

      expect(el.textContent).toContain('No bookings yet.');
    });

    it('filters by status with the chips', async () => {
      await create([makeRegistration()]);
      expect(button('All').getAttribute('aria-pressed')).toBe('true');

      button('Waitlisted').click();
      await settle();
      const req = http.expectOne(isHistory);
      expect(req.request.params.get('status')).toBe('WAITLISTED');
      req.flush([]);
      fixture.detectChanges();
      expect(el.textContent).toContain('The waitlist is empty.');
      expect(button('Waitlisted').getAttribute('aria-pressed')).toBe('true');

      button('All').click();
      await settle();
      expect(http.expectOne(isHistory).request.params.has('status')).toBe(false);
    });

    it('reloads when the page asks it to', async () => {
      await create([makeRegistration()]);

      fixture.componentRef.setInput('refreshKey', 1);
      await settle();

      http.expectOne(isHistory).flush([makeRegistration()]);
    });
  });

  describe('cancelling a booking', () => {
    it('hides the Cancel action from an admin', async () => {
      await create([makeRegistration()], 'ADMIN');

      expect(el.querySelector('tbody button')).toBeNull();
    });

    it('confirms, takes an optional reason, and says it is done', async () => {
      await create([makeRegistration()]);
      confirm.mockResolvedValue({ confirmed: true, reason: 'Called to cancel' });

      button('Cancel').click();
      await settle();

      expect(confirm).toHaveBeenCalledWith(
        expect.objectContaining({
          danger: true,
          askReason: true,
          message: 'The seat will be freed. The record is kept.',
        }),
      );
      const req = http.expectOne(`${API}/registrations/r-1/cancel`);
      expect(req.request.body).toEqual({ reason: 'Called to cancel' });
      req.flush({ cancelled: makeRegistration({ status: 'CANCELLED' }), promoted: null });
      await settle();

      expect(toasts.success).toHaveBeenCalledWith("Priya Shah's booking is cancelled.");
      expect(toasts.toasts().map((t) => t.message)).toEqual(["Priya Shah's booking is cancelled."]);
      expect(changed).toHaveBeenCalledTimes(1);
    });

    it('does nothing when the person backs out', async () => {
      await create([makeRegistration()]);
      confirm.mockResolvedValue({ confirmed: false, reason: null });

      button('Cancel').click();
      await settle();

      http.expectNone(`${API}/registrations/r-1/cancel`);
      expect(changed).not.toHaveBeenCalled();
    });

    it('tells staff to phone whoever moved up from the waitlist', async () => {
      await create([makeRegistration()]);
      confirm.mockResolvedValue({ confirmed: true, reason: null });

      button('Cancel').click();
      await settle();
      http.expectOne(`${API}/registrations/r-1/cancel`).flush({
        cancelled: makeRegistration({ status: 'CANCELLED' }),
        promoted: makeRegistration({ id: 'r-9', attendeeName: 'Sam Lee' }),
      });
      await settle();

      expect(toasts.show).toHaveBeenCalledWith(
        'Sam Lee moved up from the waitlist — please call them to confirm.',
        'info',
        expect.any(Number),
      );
    });

    it('words the question differently for someone on the waitlist', async () => {
      await create([makeRegistration({ status: 'WAITLISTED', waitlistPosition: 1 })]);
      confirm.mockResolvedValue({ confirmed: false, reason: null });

      button('Cancel').click();
      await settle();

      expect(confirm).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'They will leave the waitlist. The record is kept.' }),
      );
    });

    it('explains and refreshes when a colleague got there first', async () => {
      await create([makeRegistration()]);
      confirm.mockResolvedValue({ confirmed: true, reason: null });

      button('Cancel').click();
      await settle();
      http.expectOne(`${API}/registrations/r-1/cancel`).flush(problem('ALREADY_CANCELLED'), PROBLEM_HEADERS);
      await settle();

      expect(toasts.toasts().map((t) => t.message)).toEqual([ALREADY_CANCELLED_MESSAGE]);
      expect(changed).toHaveBeenCalledTimes(1);
    });
  });
});
