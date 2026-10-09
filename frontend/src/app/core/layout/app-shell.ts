import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { navItemsFor, roleLabel } from '@core/auth/role';
import { SessionStore } from '@core/auth/session.store';
import { ConfirmDialog } from '@shared/ui/confirm-dialog';

import { ThemeMode, ThemeService } from './theme.service';
import { ToastHost } from './toast-host';

const THEME_LABELS: Record<ThemeMode, string> = {
  light: 'Light',
  dark: 'Dark',
  system: 'Auto',
};

@Component({
  selector: 'sw-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastHost, ConfirmDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-canvas text-ink">
      <header class="border-b border-line bg-surface">
        <div
          class="mx-auto flex max-w-content flex-wrap items-center gap-x-8 gap-y-3 px-4 py-4 sm:px-6"
        >
          <a routerLink="/" class="font-serif text-2xl font-semibold tracking-tight">Seatwise</a>

          <nav aria-label="Main" class="order-last w-full sm:order-none sm:w-auto sm:flex-1">
            <ul class="flex gap-1">
              @for (item of navItems(); track item.path) {
                <li>
                  <a
                    [routerLink]="item.path"
                    routerLinkActive="bg-accent-soft"
                    ariaCurrentWhenActive="page"
                    class="block rounded-button px-3 py-1.5 text-sm font-medium hover:bg-surface-muted"
                  >
                    {{ item.label }}
                  </a>
                </li>
              }
            </ul>
          </nav>

          <div class="ml-auto flex items-center gap-3 text-sm">
            @if (session.me(); as me) {
              <span class="hidden sm:inline">{{ me.fullName }}</span>
              <span
                class="rounded-button border border-line bg-surface-muted px-2 py-0.5 text-xs font-medium"
              >
                {{ roleName() }}
              </span>
            }
            <button
              type="button"
              class="rounded-button border border-line px-3 py-1.5 font-medium hover:bg-surface-muted"
              [attr.aria-label]="'Colour theme: ' + themeLabel() + '. Switch theme.'"
              (click)="theme.cycle()"
            >
              {{ themeLabel() }}
            </button>
            <button
              type="button"
              class="rounded-button px-3 py-1.5 font-medium hover:bg-surface-muted"
              (click)="session.logout()"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main class="mx-auto max-w-content px-4 py-8 sm:px-6 sm:py-10">
        <router-outlet />
      </main>
    </div>

    <sw-toast-host />
    <sw-confirm-dialog />
  `,
})
export class AppShell {
  protected readonly session = inject(SessionStore);
  protected readonly theme = inject(ThemeService);

  protected readonly navItems = computed(() => {
    const role = this.session.role();
    return role === null ? [] : navItemsFor(role);
  });
  protected readonly roleName = computed(() => {
    const role = this.session.role();
    return role === null ? '' : roleLabel(role);
  });
  protected readonly themeLabel = computed(() => THEME_LABELS[this.theme.mode()]);
}
