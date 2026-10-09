import { ChangeDetectionStrategy, Component } from '@angular/core';

import { EmptyState } from '@shared/ui/empty-state';

@Component({
  selector: 'sw-staff-accounts-page',
  imports: [EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-6 text-3xl">Staff accounts</h1>
    <sw-empty-state heading="Coming in the next build step" />
  `,
})
export class StaffAccountsPage {}
