import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { Router } from '@angular/router';

import { SessionStore } from '@core/auth/session.store';
import { messageFor } from '@core/http/messages';
import { AccountDeactivated } from '@core/layout/account-deactivated';
import { AppShell } from '@core/layout/app-shell';
import { Button } from '@shared/ui/button';
import { Spinner } from '@shared/ui/spinner';

@Component({
  selector: 'sw-root',
  imports: [AppShell, AccountDeactivated, Button, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!session.isLoaded()) {
      <div class="flex min-h-screen items-center justify-center text-ink-muted">
        <sw-spinner size="lg" label="Signing you in" />
      </div>
    } @else if (session.isDeactivated()) {
      <sw-account-deactivated />
    } @else if (session.loadError()) {
      <main class="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 class="text-2xl">We couldn't open Seatwise</h1>
        <p class="text-ink-muted">{{ errorMessage() }}</p>
        <div class="flex gap-2">
          <sw-button variant="primary" (click)="retry()">Try again</sw-button>
          <sw-button (click)="session.logout()">Sign out</sw-button>
        </div>
      </main>
    } @else {
      <sw-app-shell />
    }
  `,
})
export class App {
  protected readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  protected readonly errorMessage = computed(() => {
    const error = this.session.loadError();
    return error ? messageFor(error) : '';
  });

  constructor() {
    void this.session.load();
  }

  protected async retry(): Promise<void> {
    await this.session.reload();
    if (this.session.loadError() === null) {
      // Routes were skipped while the profile was missing; start again from the landing page.
      await this.router.navigateByUrl('/');
    }
  }
}
