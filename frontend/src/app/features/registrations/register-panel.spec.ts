import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Role } from '@core/auth/role';
import { ToastService } from '@core/layout/toast.service';
import {
  API,
  PROBLEM_HEADERS,
  makeRegistration,
  makeWorkshop,
  problem,
  provideApiTesting,
} from '@features/workshops/workshop.fixtures';
import { Workshop } from '@features/workshops/workshop.models';

import { DUPLICATE_MESSAGE, FULL_RACE_MESSAGE, FULL_UPFRONT_MESSAGE, RegisterPanel } from './register-panel';

describe('RegisterPanel', () => {
  let fixture: ComponentFixture<RegisterPanel>;
  let http: HttpTestingController;
  let toasts: ToastService;
  let changed: jest.Mock;
  let el: HTMLElement;

  const isRegister = (r: HttpRequest<unknown>) => r.url === `${API}/workshops/w-1/registrations`;

  function create(workshop: Workshop = makeWorkshop(), role: Role | null = 'STAFF'): void {
    TestBed.configureTestingModule({ providers: provideApiTesting(role).providers });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    jest.spyOn(toasts, 'success');
    fixture = TestBed.createComponent(RegisterPanel);
    fixture.componentRef.setInput('workshop', workshop);
    changed = jest.fn();
    fixture.componentInstance.changed.subscribe(changed);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function type(control: 'attendeeName' | 'attendeeEmail', value: string): void {
    const input = el.querySelector(`[formcontrolname="${control}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function fill(name = 'Priya Shah', email = 'priya@example.com'): void {
    type('attendeeName', name);
    type('attendeeEmail', email);
  }

  function button(label: RegExp | string): HTMLButtonElement {
    const found = Array.from(el.querySelectorAll('button')).find((b) =>
      typeof label === 'string' ? b.textContent?.trim() === label : label.test(b.textContent ?? ''),
    );
    if (!found) {
      throw new Error(`No button ${String(label)}`);
    }
    return found;
  }

  async function submit(): Promise<void> {
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  describe('registering', () => {
    it('registers, thanks the person by name, clears the form and tells the page', async () => {
      create();
      fill();

      await submit();
      const req = http.expectOne(isRegister);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        attendeeName: 'Priya Shah',
        attendeeEmail: 'priya@example.com',
        joinWaitlistIfFull: false,
      });
      req.flush(makeRegistration(), { status: 201, statusText: 'Created' });
      fixture.detectChanges();

      expect(toasts.success).toHaveBeenCalledWith('Priya Shah is registered.');
      expect((el.querySelector('[formcontrolname="attendeeName"]') as HTMLInputElement).value).toBe('');
      expect((el.querySelector('[formcontrolname="attendeeEmail"]') as HTMLInputElement).value).toBe('');
      expect(changed).toHaveBeenCalledTimes(1);
    });

    it('blocks a double click while the request is in flight', async () => {
      create();
      fill();

      await submit();
      expect(button('Register').disabled).toBe(true);
      await submit();
      await submit();

      const req = http.expectOne(isRegister); // exactly one
      req.flush(makeRegistration(), { status: 201, statusText: 'Created' });
      fixture.detectChanges();
      expect(button('Register').disabled).toBe(false);
    });

    it('asks for what is missing instead of sending', async () => {
      create();
      fill('', 'not-an-email');

      await submit();

      http.expectNone(isRegister);
      expect(el.textContent).toContain("Enter the attendee's name.");
      expect(el.textContent).toContain("That email address doesn't look right.");
    });

    it('shows field messages from the server next to the field', async () => {
      create();
      fill();

      await submit();
      http
        .expectOne(isRegister)
        .flush(
          problem('VALIDATION_FAILED', 'bad', [
            { field: 'attendeeEmail', message: 'Use a work or home address.' },
          ]),
          { status: 400, statusText: 'Bad Request' },
        );
      fixture.detectChanges();

      expect(el.textContent).toContain('Use a work or home address.');
    });
  });

  describe('when the last seat goes', () => {
    it('explains, offers the waitlist and refreshes the seat count', async () => {
      create();
      fill();

      await submit();
      http.expectOne(isRegister).flush(problem('WORKSHOP_FULL'), PROBLEM_HEADERS);
      fixture.detectChanges();

      expect(el.textContent).toContain(FULL_RACE_MESSAGE);
      expect(button('Add Priya to the waitlist instead')).toBeDefined();
      expect(el.textContent).not.toContain('WORKSHOP_FULL');
      expect(changed).toHaveBeenCalledTimes(1);
      // The person's details are kept for the next step.
      expect((el.querySelector('[formcontrolname="attendeeEmail"]') as HTMLInputElement).value).toBe(
        'priya@example.com',
      );
    });

    it('re-posts with the waitlist flag and reports the position', async () => {
      create();
      fill();
      await submit();
      http.expectOne(isRegister).flush(problem('WORKSHOP_FULL'), PROBLEM_HEADERS);
      fixture.detectChanges();

      button('Add Priya to the waitlist instead').click();
      fixture.detectChanges();
      const retry = http.expectOne(isRegister);
      expect(retry.request.body).toEqual({
        attendeeName: 'Priya Shah',
        attendeeEmail: 'priya@example.com',
        joinWaitlistIfFull: true,
      });
      retry.flush(makeRegistration({ status: 'WAITLISTED', waitlistPosition: 3 }), {
        status: 201,
        statusText: 'Created',
      });
      fixture.detectChanges();

      expect(toasts.success).toHaveBeenCalledWith('Priya Shah is on the waitlist (position 3).');
      expect(el.textContent).not.toContain(FULL_RACE_MESSAGE);
      expect(changed).toHaveBeenCalledTimes(2);
    });

    it('does not re-send when Enter is pressed during the offer', async () => {
      create();
      fill();
      await submit();
      http.expectOne(isRegister).flush(problem('WORKSHOP_FULL'), PROBLEM_HEADERS);
      fixture.detectChanges();

      (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      http.expectNone(isRegister);
    });

    it('drops the offer when the details are edited', async () => {
      create();
      fill();
      await submit();
      http.expectOne(isRegister).flush(problem('WORKSHOP_FULL'), PROBLEM_HEADERS);
      fixture.detectChanges();

      type('attendeeName', 'Priya S Shah');

      expect(el.textContent).not.toContain(FULL_RACE_MESSAGE);
      expect(button('Register')).toBeDefined();
    });
  });

  describe('when the workshop is already full', () => {
    it('offers the waitlist upfront and posts with the waitlist flag', async () => {
      create(makeWorkshop({ status: 'FULL', seatsLeft: 0, seatsTaken: 20 }));

      expect(el.textContent).toContain(FULL_UPFRONT_MESSAGE);
      expect(Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim())).toEqual([
        'Add to waitlist',
      ]);
      fill();
      await submit();

      const req = http.expectOne(isRegister);
      expect(req.request.body).toMatchObject({ joinWaitlistIfFull: true });
      req.flush(makeRegistration({ status: 'WAITLISTED', waitlistPosition: 1 }), {
        status: 201,
        statusText: 'Created',
      });
      expect(toasts.success).toHaveBeenCalledWith('Priya Shah is on the waitlist (position 1).');
    });
  });

  describe('other refusals', () => {
    it('puts a duplicate under the email field in plain words', async () => {
      create();
      fill();

      await submit();
      http.expectOne(isRegister).flush(problem('DUPLICATE_REGISTRATION'), PROBLEM_HEADERS);
      fixture.detectChanges();

      const field = el.querySelector('[formcontrolname="attendeeEmail"]')?.closest('sw-form-field');
      expect(field?.textContent).toContain(DUPLICATE_MESSAGE);
      expect(el.textContent).not.toContain('DUPLICATE_REGISTRATION');
      expect(changed).not.toHaveBeenCalled();
    });

    it('explains a workshop that closed meanwhile', async () => {
      create();
      fill();

      await submit();
      http.expectOne(isRegister).flush(problem('WORKSHOP_NOT_OPEN'), PROBLEM_HEADERS);
      fixture.detectChanges();

      expect(el.textContent).toContain('This workshop is no longer open for bookings.');
      expect(changed).toHaveBeenCalledTimes(1);
    });
  });

  describe('who sees it', () => {
    it.each(['MANAGER', 'STAFF'] as const)('shows it to %s', (role) => {
      create(makeWorkshop(), role);

      expect(el.querySelector('form')).not.toBeNull();
    });

    it.each(['CANCELLED', 'COMPLETED', 'IN_PROGRESS'] as const)(
      'hides it when the workshop is %s',
      (status) => {
        create(makeWorkshop({ status }));

        expect(el.querySelector('form')).toBeNull();
      },
    );

    it('hides it from an admin', () => {
      create(makeWorkshop(), 'ADMIN');

      expect(el.querySelector('form')).toBeNull();
    });
  });
});
