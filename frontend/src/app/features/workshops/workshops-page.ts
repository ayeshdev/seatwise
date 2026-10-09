import { ChangeDetectionStrategy, Component } from '@angular/core';

import { EmptyState } from '@shared/ui/empty-state';

@Component({
  selector: 'sw-workshops-page',
  imports: [EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-6 text-3xl">Workshops</h1>
    <sw-empty-state heading="Coming in the next build step" />
  `,
})
export class WorkshopsPage {}
