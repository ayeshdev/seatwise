import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { ActivityDateRange } from './activity-date-range';
import {
  ACTIVITY_KINDS,
  ActivityFilters,
  ActivityKind,
  DateRange,
  entityTypeFor,
  filtersFromParams,
  filtersToParams,
} from './activity-filters';
import { ActivityTimeline } from './activity-timeline';

const CHIP = 'rounded-button border px-3 py-1.5 text-sm font-medium transition-colors duration-150';
const CHIP_ON = `${CHIP} border-accent bg-accent-soft text-ink`;
const CHIP_OFF = `${CHIP} border-line bg-surface text-ink hover:bg-surface-muted`;

/** Managers and front-desk staff: what changed on workshops and bookings, and who did it. */
@Component({
  selector: 'sw-activity-page',
  imports: [ActivityDateRange, ActivityTimeline],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-6 text-3xl">Activity</h1>

    <section
      class="flex flex-wrap items-end gap-x-6 gap-y-4 rounded-card border border-line bg-surface-muted p-4"
      aria-label="Filter activity"
    >
      <div role="group" aria-labelledby="sw-activity-show" class="flex flex-col gap-1.5">
        <span id="sw-activity-show" class="text-sm font-medium">Show</span>
        <div class="flex flex-wrap gap-2">
          @for (option of kinds; track option.label) {
            <button
              type="button"
              [class]="filters().kind === option.value ? chipOn : chipOff"
              [attr.aria-pressed]="filters().kind === option.value"
              (click)="selectKind(option.value)"
            >
              {{ option.label }}
            </button>
          }
        </div>
      </div>
      <sw-activity-date-range [range]="filters()" (rangeChange)="selectRange($event)" />
    </section>

    <div class="mt-6 rounded-card border border-line bg-surface p-5">
      <sw-activity-timeline
        [entityType]="entityType()"
        [from]="filters().from"
        [to]="filters().to"
        [showLinks]="true"
      />
    </div>
  `,
})
export class ActivityPage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly kinds = ACTIVITY_KINDS;
  protected readonly chipOn = CHIP_ON;
  protected readonly chipOff = CHIP_OFF;

  protected readonly filters = toSignal(this.route.queryParamMap.pipe(map(filtersFromParams)), {
    initialValue: filtersFromParams(this.route.snapshot.queryParamMap),
  });

  protected readonly entityType = computed(() => entityTypeFor(this.filters().kind));

  protected selectKind(kind: ActivityKind | null): void {
    this.update({ ...this.filters(), kind });
  }

  protected selectRange(range: DateRange): void {
    this.update({ ...this.filters(), ...range });
  }

  private update(filters: ActivityFilters): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: filtersToParams(filters),
      replaceUrl: true,
    });
  }
}
