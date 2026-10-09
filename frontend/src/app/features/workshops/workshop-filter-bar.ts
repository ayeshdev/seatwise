import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  output,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';

import { Button } from '@shared/ui/button';

import {
  DATE_PRESETS,
  DatePreset,
  STATUS_OPTIONS,
  WorkshopFilters,
  defaultFilters,
  resolveDateRange,
} from './workshop-filters';
import type { Location, WorkshopStatus } from './workshop.models';

export const SEARCH_DEBOUNCE_MS = 200;

const CHIP = 'rounded-button border px-3 py-1.5 text-sm font-medium transition-colors duration-150';
const CHIP_ON = `${CHIP} border-accent bg-accent-soft text-ink`;
const CHIP_OFF = `${CHIP} border-line bg-surface text-ink hover:bg-surface-muted`;

/**
 * The filter controls above the workshop table. It owns no state of its own: it shows the filters
 * it is given and reports every change as a complete new set (the page puts them in the URL).
 */
@Component({
  selector: 'sw-workshop-filter-bar',
  imports: [ReactiveFormsModule, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section
      class="flex flex-col gap-4 rounded-card border border-line bg-surface-muted p-4"
      aria-label="Filter workshops"
    >
      <div class="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div role="group" aria-labelledby="sw-filter-when" class="flex flex-col gap-1.5">
          <span id="sw-filter-when" class="text-sm font-medium">When</span>
          <div class="flex flex-wrap gap-2">
            @for (preset of presets; track preset.value) {
              <button
                type="button"
                [class]="filters().preset === preset.value ? chipOn : chipOff"
                [attr.aria-pressed]="filters().preset === preset.value"
                (click)="selectPreset(preset.value)"
              >
                {{ preset.label }}
              </button>
            }
          </div>
        </div>

        @if (filters().preset === 'custom') {
          <div class="flex flex-wrap items-end gap-3">
            <div>
              <label class="mb-1 block text-sm font-medium" for="sw-filter-from">From</label>
              <input
                id="sw-filter-from"
                type="date"
                class="block border-line bg-surface text-ink"
                [formControl]="fromControl"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium" for="sw-filter-to">To</label>
              <input
                id="sw-filter-to"
                type="date"
                class="block border-line bg-surface text-ink"
                [attr.min]="filters().from"
                [formControl]="toControl"
              />
            </div>
          </div>
        }

        <div class="min-w-48 flex-1">
          <label class="mb-1 block text-sm font-medium" for="sw-filter-search">Search</label>
          <input
            #searchInput
            id="sw-filter-search"
            type="search"
            class="block w-full border-line bg-surface text-ink"
            placeholder="Title, code, instructor or place"
            autocomplete="off"
            [formControl]="searchControl"
          />
        </div>
      </div>

      <div class="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div role="group" aria-labelledby="sw-filter-status" class="flex flex-col gap-1.5">
          <span id="sw-filter-status" class="text-sm font-medium">Status</span>
          <div class="flex flex-wrap gap-2">
            @for (option of statusOptions; track option.value) {
              <button
                type="button"
                [class]="isStatusOn(option.value) ? chipOn : chipOff"
                [attr.aria-pressed]="isStatusOn(option.value)"
                (click)="toggleStatus(option.value)"
              >
                {{ option.label }}
              </button>
            }
          </div>
        </div>

        <div>
          <label class="mb-1 block text-sm font-medium" for="sw-filter-location">Location</label>
          <select
            id="sw-filter-location"
            class="block min-w-40 border-line bg-surface text-ink"
            [formControl]="locationControl"
          >
            <option value="">All locations</option>
            @for (location of locations(); track location.id) {
              <option [value]="location.id">{{ location.name }}</option>
            }
          </select>
        </div>

        <div class="flex flex-col gap-1.5">
          <span id="sw-filter-seats" class="text-sm font-medium">Seats</span>
          <button
            type="button"
            role="switch"
            aria-labelledby="sw-filter-seats"
            [class]="filters().hasSeats ? chipOn : chipOff"
            [attr.aria-checked]="filters().hasSeats"
            (click)="toggleHasSeats()"
          >
            Has seats
          </button>
        </div>

        <div class="ml-auto">
          <sw-button variant="ghost" size="sm" (click)="reset()">Reset filters</sw-button>
        </div>
      </div>
    </section>
  `,
})
export class WorkshopFilterBar {
  readonly filters = input.required<WorkshopFilters>();
  readonly locations = input<readonly Location[]>([]);
  readonly filtersChange = output<WorkshopFilters>();

  protected readonly presets = DATE_PRESETS;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly chipOn = CHIP_ON;
  protected readonly chipOff = CHIP_OFF;

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly locationControl = new FormControl('', { nonNullable: true });
  protected readonly fromControl = new FormControl('', { nonNullable: true });
  protected readonly toControl = new FormControl('', { nonNullable: true });

  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  private readonly doc = inject(DOCUMENT);

  constructor() {
    // Show whatever the address bar says (including after browser back), but never overwrite the
    // search box while someone is typing in it.
    effect(() => {
      const f = this.filters();
      untracked(() => {
        const box = this.searchInput()?.nativeElement;
        const typing = box !== undefined && this.doc.activeElement === box;
        if (!typing && this.searchControl.value !== f.q) {
          this.searchControl.setValue(f.q, { emitEvent: false });
        }
        this.locationControl.setValue(f.locationId ?? '', { emitEvent: false });
        this.fromControl.setValue(f.from ?? '', { emitEvent: false });
        this.toControl.setValue(f.to ?? '', { emitEvent: false });
      });
    });

    this.searchControl.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((value) => value.trim()),
        distinctUntilChanged(),
        filter((q) => q !== this.filters().q),
        takeUntilDestroyed(),
      )
      .subscribe((q) => this.emit({ q }));

    this.locationControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.emit({ locationId: id === '' ? null : id }));

    this.fromControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.emit({ from: value === '' ? null : value }));

    this.toControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.emit({ to: value === '' ? null : value }));
  }

  protected selectPreset(preset: DatePreset): void {
    if (preset === 'custom') {
      // Start from the range the person was just looking at, so the dates are never blank.
      const { from, to } = resolveDateRange(this.filters());
      this.emit({ preset, from, to });
    } else {
      this.emit({ preset, from: null, to: null });
    }
  }

  protected isStatusOn(status: WorkshopStatus): boolean {
    return this.filters().statuses.includes(status);
  }

  protected toggleStatus(status: WorkshopStatus): void {
    const current = this.filters().statuses;
    const next = current.includes(status)
      ? current.filter((s) => s !== status)
      : STATUS_OPTIONS.map((o) => o.value).filter((s) => s === status || current.includes(s));
    this.emit({ statuses: next });
  }

  protected toggleHasSeats(): void {
    this.emit({ hasSeats: !this.filters().hasSeats });
  }

  protected reset(): void {
    this.filtersChange.emit(defaultFilters());
  }

  /** Any change goes back to the first page. */
  private emit(change: Partial<WorkshopFilters>): void {
    this.filtersChange.emit({ ...this.filters(), ...change, page: 0 });
  }
}
