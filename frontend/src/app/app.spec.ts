import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Me, SessionStore } from '@core/auth/session.store';
import { ProblemError } from '@core/http/problem';

import { App } from './app';

class FakeSession {
  readonly me = signal<Me | null>(null);
  readonly role = computed(() => this.me()?.role ?? null);
  readonly isLoaded = signal(false);
  readonly loadError = signal<ProblemError | null>(null);
  readonly isDeactivated = computed(() => this.loadError()?.code === 'ACCOUNT_INACTIVE');
  load = jest.fn(async () => undefined);
  reload = jest.fn(async () => undefined);
  logout = jest.fn(async () => undefined);
}

describe('App', () => {
  let session: FakeSession;

  beforeEach(async () => {
    session = new FakeSession();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: SessionStore, useValue: session }],
    }).compileComponents();
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function signIn(role: Me['role']): void {
    session.me.set({ id: '1', email: 'a@b.c', fullName: 'Asha Rao', role });
    session.isLoaded.set(true);
  }

  it('loads the session and shows a spinner meanwhile', () => {
    const root = render();

    expect(session.load).toHaveBeenCalled();
    expect(root.textContent).toContain('Signing you in');
    expect(root.querySelector('header')).toBeNull();
  });

  it('shows the admin shell with admin navigation, name and role', () => {
    signIn('ADMIN');
    const root = render();
    const links = Array.from(root.querySelectorAll('nav a')).map((a) => a.textContent?.trim());

    expect(root.querySelector('header')?.textContent).toContain('Seatwise');
    expect(links).toEqual(['Staff accounts', 'Account activity']);
    expect(root.textContent).toContain('Asha Rao');
    expect(root.textContent).toContain('Admin');
    expect(root.textContent).toContain('Sign out');
  });

  it.each(['MANAGER', 'STAFF'] as const)('shows workshop navigation for %s', (role) => {
    signIn(role);
    const links = Array.from(render().querySelectorAll('nav a')).map((a) => a.textContent?.trim());

    expect(links).toEqual(['Workshops', 'Activity']);
  });

  it('shows the deactivated page with a sign out button', () => {
    session.loadError.set(new ProblemError(403, 'ACCOUNT_INACTIVE', null));
    session.isLoaded.set(true);
    const root = render();

    expect(root.textContent).toContain('Your account has been deactivated.');
    expect(root.textContent).toContain('Please speak to an administrator.');
    expect(root.querySelector('header')).toBeNull();

    const signOut = Array.from(root.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Sign out',
    );
    signOut?.click();
    expect(session.logout).toHaveBeenCalled();
  });

  it('offers a retry when the profile fails to load for another reason', () => {
    session.loadError.set(new ProblemError(0, 'NETWORK', null));
    session.isLoaded.set(true);
    const root = render();

    expect(root.textContent).toContain("We couldn't open Seatwise");
    expect(root.textContent).toContain('Check your internet connection');
    expect(root.textContent).toContain('Try again');
  });
});
