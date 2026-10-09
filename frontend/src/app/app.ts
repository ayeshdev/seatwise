import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'sw-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-canvas text-ink">
      <header class="border-b border-line bg-surface">
        <div class="mx-auto max-w-5xl px-4 py-5 sm:px-6">
          <h1 class="text-2xl tracking-tight">Seatwise</h1>
          <p class="mt-1 text-sm text-ink-muted">Workshop registrations</p>
        </div>
      </header>
      <main class="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <router-outlet />
      </main>
    </div>
  `,
})
export class App {}
