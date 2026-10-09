import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { provideRuntimeConfig } from '@core/config/runtime-config';
import { ToastService } from '@core/layout/toast.service';

import { StaffAccountCreatePage } from './staff-account-create-page';
import { PASSWORD_ALPHABET, StaffAccount } from './staff-accounts.model';

@Component({ template: '' })
class Blank {}

const CREATED: StaffAccount = {
  id: 'new-id',
  email: 'sam@seatwise.local',
  fullName: 'Sam Patel',
  role: 'MANAGER',
  active: true,
  createdAt: '2026-10-09T09:00:00Z',
  updatedAt: '2026-10-09T09:00:00Z',
  version: 0,
};

describe('StaffAccountCreatePage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;
  let toasts: ToastService;

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
          { path: 'staff-accounts/new', component: StaffAccountCreatePage },
          { path: 'staff-accounts', component: Blank },
          { path: 'staff-accounts/:id', component: Blank },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    toasts = TestBed.inject(ToastService);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/staff-accounts/new', StaffAccountCreatePage);
  });

  afterEach(() => http.verify());

  const root = () => harness.routeNativeElement as HTMLElement;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
  };
  const nameInput = () =>
    root().querySelector('input[formcontrolname=fullName]') as HTMLInputElement;
  const emailInput = () => root().querySelector('input[type=email]') as HTMLInputElement;
  const passwordInput = () =>
    root().querySelector('sw-temporary-password-field input') as HTMLInputElement;
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function chooseRole(label: string): void {
    const option = Array.from(root().querySelectorAll('label')).find((l) =>
      l.textContent?.includes(label),
    );
    const radio = option?.querySelector('input[type=radio]') as HTMLInputElement;
    radio.click();
  }

  function fillValid(): void {
    type(nameInput(), 'Sam Patel');
    type(emailInput(), 'sam@seatwise.local');
    chooseRole('Programme manager');
    type(passwordInput(), 'Kd7mQ2xRt9bW4c');
  }

  it('describes every role in one plain line', () => {
    const text = root().textContent ?? '';
    expect(root().querySelectorAll('input[type=radio]')).toHaveLength(3);
    expect(text).toContain("Creates staff accounts and sets roles. Doesn't handle bookings.");
    expect(text).toContain('Schedules and edits workshops, and can book attendees.');
    expect(text).toContain('Registers and cancels attendees.');
    expect(text).toContain(
      "They'll be asked to choose their own password the first time they sign in.",
    );
  });

  it('does not submit an empty or weak form, and says what to fix', async () => {
    type(emailInput(), 'not-an-email');
    type(passwordInput(), 'short');
    button('Create account').click();
    await settle();

    http.expectNone('/api/v1/staff-accounts');
    const text = root().textContent ?? '';
    expect(text).toContain("Enter the person's full name.");
    expect(text).toContain("That doesn't look like an email address");
    expect(text).toContain('Use at least 10 characters.');
  });

  it('refuses a password that contains the email', async () => {
    type(emailInput(), 'sam@seatwise.local');
    type(passwordInput(), 'xx-sam@seatwise.local-xx');
    button('Create account').click();
    await settle();

    expect(root().textContent).toContain("The password can't contain the email address.");
    http.expectNone('/api/v1/staff-accounts');
  });

  it('generates a 14 character password from the readable alphabet', async () => {
    button('Generate').click();
    await settle();

    const value = passwordInput().value;
    expect(value).toHaveLength(14);
    for (const char of value) {
      expect(PASSWORD_ALPHABET).toContain(char);
    }
  });

  it('copies the password and tells the person', async () => {
    const writeText = jest.fn(async () => undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    type(passwordInput(), 'Kd7mQ2xRt9bW4c');
    await settle();

    button('Copy').click();
    await settle();

    expect(writeText).toHaveBeenCalledWith('Kd7mQ2xRt9bW4c');
    expect(toasts.toasts().map((t) => t.message)).toContain('Password copied.');
  });

  it('creates the account, opens its page and reminds the admin to share the password', async () => {
    fillValid();
    button('Create account').click();
    await settle();

    const req = http.expectOne('/api/v1/staff-accounts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'sam@seatwise.local',
      fullName: 'Sam Patel',
      role: 'MANAGER',
      temporaryPassword: 'Kd7mQ2xRt9bW4c',
    });
    req.flush(CREATED, { status: 201, statusText: 'Created' });
    await settle();

    expect(router.url).toBe('/staff-accounts/new-id');
    expect(toasts.toasts().map((t) => t.message)).toContain(
      'Account created for Sam Patel. Share the temporary password with them in person.',
    );
  });

  it('shows EMAIL_IN_USE next to the email field and stays on the form', async () => {
    fillValid();
    button('Create account').click();
    await settle();

    http
      .expectOne('/api/v1/staff-accounts')
      .flush(
        { code: 'EMAIL_IN_USE', detail: 'duplicate' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();

    expect(router.url).toBe('/staff-accounts/new');
    const emailError = emailInput().closest('sw-form-field')?.textContent ?? '';
    expect(emailError).toContain('That email address already belongs to another account');
    expect(emailInput().getAttribute('aria-invalid')).toBe('true');
    expect(root().textContent).not.toContain('EMAIL_IN_USE');

    // Editing the email clears the server message.
    type(emailInput(), 'sam.patel@seatwise.local');
    await settle();
    expect(emailInput().closest('sw-form-field')?.textContent).not.toContain('already belongs');
  });

  it('puts server field errors on the matching field', async () => {
    fillValid();
    button('Create account').click();
    await settle();

    http.expectOne('/api/v1/staff-accounts').flush(
      {
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'temporaryPassword', message: 'Pick a password that is harder to guess.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    const field = passwordInput().closest('sw-form-field')?.textContent ?? '';
    expect(field).toContain('Pick a password that is harder to guess.');
  });

  it('explains other failures in a banner without codes', async () => {
    fillValid();
    button('Create account').click();
    await settle();

    http
      .expectOne('/api/v1/staff-accounts')
      .flush(
        { code: 'IDENTITY_UNAVAILABLE', detail: 'keycloak down' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await settle();

    const alert = root().querySelector('[role=alert]')?.textContent ?? '';
    expect(alert).toContain('Sign-in service is busy. Please try again in a moment.');
    expect(alert).not.toContain('IDENTITY_UNAVAILABLE');
  });
});
