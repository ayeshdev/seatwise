import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { SessionStore } from '@core/auth/session.store';
import { Button } from '@shared/ui/button';

@Component({
  selector: 'sw-account-deactivated',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-4 px-6 text-center">
      <p class="font-serif text-2xl font-semibold">Seatwise</p>
      <h1 class="text-2xl">Your account has been deactivated.</h1>
      <p class="text-ink-muted">Please speak to an administrator.</p>
      <sw-button variant="primary" (click)="session.logout()">Sign out</sw-button>
    </main>
  `,
})
export class AccountDeactivated {
  protected readonly session = inject(SessionStore);
}
