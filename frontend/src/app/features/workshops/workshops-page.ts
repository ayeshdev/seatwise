import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { SessionStore } from '@core/auth/session.store';
import { Button } from '@shared/ui/button';
import { EmptyState } from '@shared/ui/empty-state';
import { Pager } from '@shared/ui/pager';
import { SeatMeter } from '@shared/ui/seat-meter';
import { StatusBadge } from '@shared/ui/status-badge';

import { WorkshopFilterBar } from './workshop-filter-bar';
import {
  WorkshopFilters,
  defaultFilters,
  filtersFromParams,
  filtersToParams,
  toSearchQuery,
} from './workshop-filters';
import { formatDay, formatTimeRange } from './workshop-format';
import { Location, WorkshopSearchResult } from './workshop.models';
import { WorkshopsService } from './workshops.service';

/** The workshop list: filters live in the address bar, results come from the search endpoint. */
@Component({
  selector: 'sw-workshops-page',
  imports: [RouterLink, Button, EmptyState, Pager, SeatMeter, StatusBadge, WorkshopFilterBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
      <h1 class="text-3xl">Workshops</h1>
      @if (isManager()) {
        <sw-button variant="primary" (click)="schedule()">Schedule workshop</sw-button>
      }
    </div>

    <sw-workshop-filter-bar
      [filters]="filters()"
      [locations]="locations()"
      (filtersChange)="onFiltersChange($event)"
    />

    @if (result()?.searchMode === 'fallback') {
      <p class="mt-3 text-sm text-ink-muted">Search is running in basic mode — exact words only.</p>
    }

    <div class="mt-6" aria-live="polite" [attr.aria-busy]="loading()">
      @if (loading() && result() === null) {
        <div
          class="overflow-hidden rounded-card border border-line bg-surface"
          role="status"
          aria-label="Loading workshops"
        >
          @for (row of skeletonRows; track row) {
            <div class="flex gap-4 border-b border-line p-4 last:border-b-0">
              <span class="h-4 w-28 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
              <span class="h-4 w-16 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
              <span class="h-4 flex-1 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
              <span class="h-4 w-24 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
            </div>
          }
        </div>
      } @else if (failed()) {
        <sw-empty-state
          heading="We couldn't load the workshops"
          description="Check your connection, then try again."
        >
          <sw-button (click)="reload()">Try again</sw-button>
        </sw-empty-state>
      } @else if (result(); as data) {
        @if (data.items.length === 0) {
          <sw-empty-state heading="No workshops match." description="Try a wider date range." />
        } @else {
          <div
            class="overflow-x-auto rounded-card border border-line bg-surface"
            [class.opacity-60]="loading()"
          >
            <table class="w-full min-w-[56rem] text-left">
              <caption class="sr-only">
                Workshops
              </caption>
              <thead class="bg-surface-muted text-sm text-ink-muted">
                <tr>
                  <th scope="col" class="px-4 py-3 font-medium">Starts</th>
                  <th scope="col" class="px-4 py-3 font-medium">Code</th>
                  <th scope="col" class="px-4 py-3 font-medium">Title</th>
                  <th scope="col" class="px-4 py-3 font-medium">Instructor</th>
                  <th scope="col" class="px-4 py-3 font-medium">Location</th>
                  <th scope="col" class="px-4 py-3 font-medium">Seats</th>
                  <th scope="col" class="px-4 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                @for (w of data.items; track w.id) {
                  <tr class="relative border-t border-line hover:bg-surface-muted">
                    <td class="px-4 py-3 align-top tabular-nums">
                      <div class="font-medium">{{ day(w.startsAt) }}</div>
                      <div class="text-sm text-ink-muted">{{ times(w.startsAt, w.endsAt) }}</div>
                    </td>
                    <td class="px-4 py-3 align-top tabular-nums">{{ w.code }}</td>
                    <td class="px-4 py-3 align-top">
                      <!-- The link covers the whole row, so the row is clickable and keyboard-reachable. -->
                      <a
                        class="font-medium after:absolute after:inset-0 after:content-['']"
                        [routerLink]="['/workshops', w.id]"
                      >
                        {{ w.title }}
                      </a>
                    </td>
                    <td class="px-4 py-3 align-top">{{ w.instructor }}</td>
                    <td class="px-4 py-3 align-top">{{ w.location.name }}</td>
                    <td class="px-4 py-3 align-top">
                      <sw-seat-meter [seatsLeft]="w.seatsLeft" [capacity]="w.capacity" />
                    </td>
                    <td class="px-4 py-3 align-top"><sw-status-badge [status]="w.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <sw-pager
              [page]="data.page"
              [size]="data.size"
              [totalItems]="data.totalItems"
              (pageChange)="onPage($event)"
            />
          </div>
        }
      }
    </div>
  `,
})
export class WorkshopsPage {
  private readonly service = inject(WorkshopsService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly skeletonRows = [1, 2, 3, 4, 5, 6];

  protected readonly filters = toSignal(
    this.route.queryParamMap.pipe(map(filtersFromParams)),
    { initialValue: filtersFromParams(this.route.snapshot.queryParamMap) },
  );
  protected readonly isManager = computed(() => this.session.role() === 'MANAGER');
  protected readonly result = signal<WorkshopSearchResult | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly locations = signal<readonly Location[]>([]);

  private readonly reloadCount = signal(0);

  constructor() {
    // Opening the list with no parameters means "this week, has seats": write that into the URL
    // so what is on screen is also what is in the address bar.
    if (this.route.snapshot.queryParamMap.keys.length === 0) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: filtersToParams(defaultFilters()),
        replaceUrl: true,
      });
    }

    this.service
      .listLocations()
      .pipe(
        catchError(() => of<Location[]>([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((locations) => this.locations.set(locations));

    // switchMap drops a slow answer if the filters changed again meanwhile.
    toObservable(computed(() => ({ filters: this.filters(), attempt: this.reloadCount() })))
      .pipe(
        map(({ filters, attempt }) => ({ filters, key: `${attempt}:${JSON.stringify(filters)}` })),
        distinctUntilChanged((a, b) => a.key === b.key),
        switchMap(({ filters }) => {
          this.loading.set(true);
          this.failed.set(false);
          return this.service.search(toSearchQuery(filters)).pipe(
            catchError(() => {
              this.failed.set(true);
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.result.set(result);
        this.loading.set(false);
      });
  }

  protected day(iso: string): string {
    return formatDay(iso);
  }

  protected times(startsAt: string, endsAt: string): string {
    return formatTimeRange(startsAt, endsAt);
  }

  protected schedule(): void {
    void this.router.navigate(['/workshops', 'new']);
  }

  protected reload(): void {
    this.reloadCount.update((n) => n + 1);
  }

  protected onFiltersChange(next: WorkshopFilters): void {
    // Typing in the search box shouldn't fill the back-button history.
    const onlyText = next.q !== this.filters().q;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: filtersToParams(next),
      replaceUrl: onlyText,
    });
  }

  protected onPage(page: number): void {
    this.onFiltersChange({ ...this.filters(), page });
  }
}
