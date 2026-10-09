import { ChangeDetectionStrategy, Component, effect, input, output, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Button } from '@shared/ui/button';

import { DateRange } from './activity-filters';

/**
 * From / To date inputs. It shows the range it is given and reports every change as a complete
 * new range; the page keeps it in the address bar. The two ends can't cross: moving one past the
 * other drags the other along.
 */
@Component({
  selector: 'sw-activity-date-range',
  imports: [ReactiveFormsModule, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="flex flex-wrap items-end gap-3">
      <div>
        <label class="mb-1 block text-sm font-medium" for="sw-activity-from">From</label>
        <input
          id="sw-activity-from"
          type="date"
          class="block border-line bg-surface text-ink"
          [formControl]="fromControl"
        />
      </div>
      <div>
        <label class="mb-1 block text-sm font-medium" for="sw-activity-to">To</label>
        <input
          id="sw-activity-to"
          type="date"
          class="block border-line bg-surface text-ink"
          [attr.min]="range().from"
          [formControl]="toControl"
        />
      </div>
      @if (range().from || range().to) {
        <sw-button variant="ghost" size="sm" (click)="clear()">Clear dates</sw-button>
      }
    </div>
  `,
})
export class ActivityDateRange {
  readonly range = input.required<DateRange>();
  readonly rangeChange = output<DateRange>();

  protected readonly fromControl = new FormControl('', { nonNullable: true });
  protected readonly toControl = new FormControl('', { nonNullable: true });

  constructor() {
    // Show whatever the address bar says (including after browser back).
    effect(() => {
      const { from, to } = this.range();
      untracked(() => {
        this.fromControl.setValue(from ?? '', { emitEvent: false });
        this.toControl.setValue(to ?? '', { emitEvent: false });
      });
    });

    this.fromControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      const from = value === '' ? null : value;
      const to = this.range().to;
      this.rangeChange.emit({ from, to: from !== null && to !== null && to < from ? from : to });
    });

    this.toControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      const to = value === '' ? null : value;
      const from = this.range().from;
      this.rangeChange.emit({ from: to !== null && from !== null && from > to ? to : from, to });
    });
  }

  protected clear(): void {
    this.rangeChange.emit({ from: null, to: null });
  }
}
