import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { ActivityDateRange } from './activity-date-range';
import { DateRange, rangeFromParams, rangeToParams } from './activity-filters';
import { ActivityTimeline } from './activity-timeline';

/** Administrators: every account change (new accounts, roles, deactivations, password resets). */
@Component({
  selector: 'sw-account-activity-page',
  imports: [ActivityDateRange, ActivityTimeline],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-6 text-3xl">Account activity</h1>

    <section
      class="rounded-card border border-line bg-surface-muted p-4"
      aria-label="Filter account activity"
    >
      <sw-activity-date-range [range]="range()" (rangeChange)="selectRange($event)" />
    </section>

    <div class="mt-6 rounded-card border border-line bg-surface p-5">
      <sw-activity-timeline
        [entityType]="entityType"
        [from]="range().from"
        [to]="range().to"
        [showLinks]="true"
      />
    </div>
  `,
})
export class AccountActivityPage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly entityType = 'STAFF_ACCOUNT' as const;
  protected readonly range = toSignal(this.route.queryParamMap.pipe(map(rangeFromParams)), {
    initialValue: rangeFromParams(this.route.snapshot.queryParamMap),
  });

  protected selectRange(range: DateRange): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: rangeToParams(range),
      replaceUrl: true,
    });
  }
}
