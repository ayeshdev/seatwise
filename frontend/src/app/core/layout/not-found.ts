import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { landingPathFor } from '@core/auth/role';
import { SessionStore } from '@core/auth/session.store';
import { EmptyState } from '@shared/ui/empty-state';

@Component({
  selector: 'sw-not-found',
  imports: [EmptyState, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sw-empty-state
      heading="We couldn't find that page"
      description="The address may be out of date, or the page may have moved."
    >
      <a
        [routerLink]="home()"
        class="inline-block rounded-button border border-accent bg-accent px-4 py-2 font-medium text-on-accent"
      >
        Go to your home page
      </a>
    </sw-empty-state>
  `,
})
export class NotFound {
  private readonly session = inject(SessionStore);

  protected readonly home = computed(() => {
    const role = this.session.role();
    return role === null ? '/' : landingPathFor(role);
  });
}
