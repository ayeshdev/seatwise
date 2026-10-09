import { ChangeDetectionStrategy, Component } from '@angular/core';

import { EmptyState } from '@shared/ui/empty-state';

@Component({
  selector: 'sw-activity-page',
  imports: [EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-6 text-3xl">Activity</h1>
    <sw-empty-state heading="Coming in the next build step" />
  `,
})
export class ActivityPage {}
