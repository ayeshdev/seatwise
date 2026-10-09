import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Me, SessionStore } from '@core/auth/session.store';
import { provideRuntimeConfig } from '@core/config/runtime-config';
import { ToastService } from '@core/layout/toast.service';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';

import { StaffAccountDetailPage } from './staff-account-detail-page';
import { StaffAccount } from './staff-accounts.model';

const SAM: StaffAccount = {
  id: 'acc-1',
  email: 'sam@seatwise.local',
  fullName: 'Sam Patel',
  role: 'STAFF',
  active: true,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-02T09:00:00Z',
  version: 3,
};
const ME: Me = { id: 'me-1', email: 'asha@seatwise.local', fullName: 'Asha Rao', role: 'ADMIN' };
const MY_ACCOUNT: StaffAccount = {
  ...SAM,
  id: 'me-1',
  email: 'asha@seatwise.local',
  fullName: 'Asha Rao',
  role: 'ADMIN',
};

describe('StaffAccountDetailPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let toasts: ToastService;
  let confirm: ConfirmDialogService;

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
        provideRouter([{ path: 'staff-accounts/:id', component: StaffAccountDetailPage }]),
        { provide: SessionStore, useValue: { me: signal<Me>(ME) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    confirm = TestBed.inject(ConfirmDialogService);
  });

  afterEach(() => http.verify());

  const root = () => harness.routeNativeElement as HTMLElement;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
  };
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as
      HTMLButtonElement | undefined;
  const toastMessages = () => toasts.toasts().map((t) => t.message);
  const nameInput = () =>
    root().querySelector('input[formcontrolname=fullName]') as HTMLInputElement;
  const radio = (label: string) =>
    Array.from(root().querySelectorAll('label'))
      .find((l) => l.textContent?.includes(label))
      ?.querySelector('input[type=radio]') as HTMLInputElement;

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function open(account: StaffAccount): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/staff-accounts/${account.id}`, StaffAccountDetailPage);
    await settle();
    http.expectOne(`/api/v1/staff-accounts/${account.id}`).flush(account);
    await settle();
  }

  it('shows the account in plain words', async () => {
    await open(SAM);

    const text = root().textContent ?? '';
    expect(root().querySelector('h1')?.textContent).toContain('Sam Patel');
    expect(text).toContain('sam@seatwise.local');
    expect(text).toContain('Front desk');
    expect(text).toContain('Active');
    expect(text).not.toContain('STAFF');
    expect(text).not.toContain('acc-1');
    expect(nameInput().value).toBe('Sam Patel');
    expect(radio('Front desk').checked).toBe(true);
  });

  it('explains when the account cannot be found', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/staff-accounts/acc-1', StaffAccountDetailPage);
    await settle();
    http
      .expectOne('/api/v1/staff-accounts/acc-1')
      .flush({ code: 'NOT_FOUND', detail: 'x' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(root().querySelector('[role=alert]')?.textContent).toContain("We couldn't find that");
  });

  it('keeps Save off until something changes', async () => {
    await open(SAM);

    const save = button('Save changes') as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    type(nameInput(), 'Sam P.');
    await settle();
    expect(save.disabled).toBe(false);
  });

  it('sends only the changed name, with the version it loaded', async () => {
    await open(SAM);

    type(nameInput(), '  Sam P.  ');
    await settle();
    button('Save changes')?.click();
    await settle();

    const req = http.expectOne('/api/v1/staff-accounts/acc-1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ fullName: 'Sam P.', version: 3 });
    req.flush({ ...SAM, fullName: 'Sam P.', version: 4 });
    await settle();

    expect(root().querySelector('h1')?.textContent).toContain('Sam P.');
    expect(toastMessages()).toContain('Changes saved.');
    expect((button('Save changes') as HTMLButtonElement).disabled).toBe(true);
  });

  it('sends the new role when it is changed', async () => {
    await open(SAM);

    radio('Programme manager').click();
    await settle();
    button('Save changes')?.click();
    await settle();

    const req = http.expectOne('/api/v1/staff-accounts/acc-1');
    expect(req.request.body).toEqual({ role: 'MANAGER', version: 3 });
    req.flush({ ...SAM, role: 'MANAGER', version: 4 });
  });

  it('shows a Reload banner on STALE_VERSION and reloads on request', async () => {
    await open(SAM);

    type(nameInput(), 'Sam P.');
    await settle();
    button('Save changes')?.click();
    await settle();
    http
      .expectOne('/api/v1/staff-accounts/acc-1')
      .flush({ code: 'STALE_VERSION', detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await settle();

    const banner = root().querySelector('[role=alert]')?.textContent ?? '';
    expect(banner).toContain('Someone else changed this account while you were editing.');
    expect(banner).not.toContain('STALE_VERSION');

    button('Reload')?.click();
    await settle();
    http
      .expectOne('/api/v1/staff-accounts/acc-1')
      .flush({ ...SAM, fullName: 'Samuel Patel', version: 4 });
    await settle();

    expect(root().querySelector('[role=alert]')).toBeNull();
    expect(nameInput().value).toBe('Samuel Patel');
  });

  it('puts a refused change in plain words', async () => {
    await open(SAM);

    radio('Admin').click();
    await settle();
    button('Save changes')?.click();
    await settle();
    http
      .expectOne('/api/v1/staff-accounts/acc-1')
      .flush({ code: 'LAST_ADMIN', detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await settle();

    const alert = root().querySelector('[role=alert]')?.textContent ?? '';
    expect(alert).toContain('There must always be at least one active administrator.');
    expect(alert).not.toContain('LAST_ADMIN');
  });

  describe('the signed-in admin looking at their own account', () => {
    beforeEach(async () => {
      await open(MY_ACCOUNT);
    });

    it('cannot change their own role or deactivate themselves', () => {
      expect(root().textContent).toContain(
        "You can't change your own role or deactivate yourself.",
      );
      for (const input of Array.from(root().querySelectorAll('input[type=radio]'))) {
        expect((input as HTMLInputElement).disabled).toBe(true);
      }
      expect((button('Deactivate account') as HTMLButtonElement).disabled).toBe(true);
      // Everything else still works.
      expect(nameInput().disabled).toBe(false);
      expect((button('Set a new temporary password') as HTMLButtonElement).disabled).toBe(false);
    });

    it('never sends the role, even when saving a new name', async () => {
      type(nameInput(), 'Asha R. Rao');
      await settle();
      button('Save changes')?.click();
      await settle();

      const req = http.expectOne('/api/v1/staff-accounts/me-1');
      expect(req.request.body).toEqual({ fullName: 'Asha R. Rao', version: 3 });
      req.flush({ ...MY_ACCOUNT, fullName: 'Asha R. Rao', version: 4 });
      await settle();

      expect(radio('Admin').disabled).toBe(true);
    });
  });

  describe('deactivating', () => {
    it('asks first, and only deactivates when confirmed', async () => {
      await open(SAM);

      button('Deactivate account')?.click();
      await settle();

      const pending = confirm.pending();
      expect(pending?.options.message).toBe(
        'Sam will no longer be able to sign in. Their booking history is kept.',
      );
      expect(pending?.options.danger).toBe(true);
      http.expectNone('/api/v1/staff-accounts/acc-1');

      confirm.answer(true);
      await settle();

      const req = http.expectOne('/api/v1/staff-accounts/acc-1');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ active: false, version: 3 });
      req.flush({ ...SAM, active: false, version: 4 });
      await settle();

      expect(root().textContent).toContain('Deactivated');
      expect(button('Reactivate account')).toBeDefined();
      expect(toastMessages().join(' ')).toContain('Account deactivated');
    });

    it('does nothing when the question is declined', async () => {
      await open(SAM);

      button('Deactivate account')?.click();
      await settle();
      confirm.answer(false);
      await settle();

      http.expectNone('/api/v1/staff-accounts/acc-1');
      expect(root().textContent).toContain('Active');
    });

    it('reactivates a deactivated account without a question', async () => {
      await open({ ...SAM, active: false });

      button('Reactivate account')?.click();
      await settle();

      expect(confirm.pending()).toBeNull();
      const req = http.expectOne('/api/v1/staff-accounts/acc-1');
      expect(req.request.body).toEqual({ active: true, version: 3 });
      req.flush({ ...SAM, active: true, version: 4 });
      await settle();

      expect(button('Deactivate account')).toBeDefined();
    });
  });

  describe('setting a new temporary password', () => {
    it('opens a dialog, saves the password and confirms with a toast', async () => {
      await open(SAM);

      button('Set a new temporary password')?.click();
      await settle();

      const dialog = root().querySelector('sw-reset-password-dialog dialog');
      expect(dialog?.hasAttribute('open')).toBe(true);
      expect(dialog?.textContent).toContain('Generate');
      expect(dialog?.textContent).toContain('Copy');

      const input = dialog?.querySelector('input') as HTMLInputElement;
      type(input, 'Kd7mQ2xRt9bW4c');
      (dialog?.querySelector('button[type=submit]') as HTMLButtonElement).click();
      await settle();

      const req = http.expectOne('/api/v1/staff-accounts/acc-1/password-reset');
      expect(req.request.body).toEqual({ temporaryPassword: 'Kd7mQ2xRt9bW4c' });
      req.flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(root().querySelector('sw-reset-password-dialog')).toBeNull();
      expect(toastMessages()).toContain(
        'New temporary password set for Sam. Share it with them in person.',
      );
    });

    it('refuses a weak password before asking the server', async () => {
      await open(SAM);

      button('Set a new temporary password')?.click();
      await settle();
      const dialog = root().querySelector('sw-reset-password-dialog dialog') as HTMLElement;
      type(dialog.querySelector('input') as HTMLInputElement, 'short');
      (dialog.querySelector('button[type=submit]') as HTMLButtonElement).click();
      await settle();

      http.expectNone('/api/v1/staff-accounts/acc-1/password-reset');
      expect(dialog.textContent).toContain('Use at least 10 characters.');
    });

    it('closes without changing anything when cancelled', async () => {
      await open(SAM);

      button('Set a new temporary password')?.click();
      await settle();
      const cancel = Array.from(root().querySelectorAll('sw-reset-password-dialog button')).find(
        (b) => b.textContent?.trim() === 'Cancel',
      ) as HTMLButtonElement;
      cancel.click();
      await settle();

      expect(root().querySelector('sw-reset-password-dialog')).toBeNull();
      http.expectNone('/api/v1/staff-accounts/acc-1/password-reset');
    });
  });
});
