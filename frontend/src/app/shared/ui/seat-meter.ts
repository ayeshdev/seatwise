import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type SeatTone = 'plenty' | 'low' | 'none';

/** Warn tone for the last few seats (1 to 3 left); full tone at zero. */
export const LOW_SEATS_THRESHOLD = 3;

export function seatTone(seatsLeft: number): SeatTone {
  if (seatsLeft <= 0) {
    return 'none';
  }
  return seatsLeft <= LOW_SEATS_THRESHOLD ? 'low' : 'plenty';
}

const FILL_CLASSES: Record<SeatTone, string> = {
  plenty: 'bg-accent',
  low: 'bg-warn',
  none: 'bg-full',
};

const TEXT_CLASSES: Record<SeatTone, string> = {
  plenty: 'text-ink',
  low: 'font-medium text-ink',
  none: 'font-medium text-full',
};

@Component({
  selector: 'sw-seat-meter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-28' },
  template: `
    <div
      role="meter"
      class="flex flex-col gap-1"
      [attr.aria-label]="accessibleLabel()"
      aria-valuemin="0"
      [attr.aria-valuemax]="total()"
      [attr.aria-valuenow]="left()"
      [attr.aria-valuetext]="accessibleLabel()"
    >
      <span class="text-sm tabular-nums" [class]="textClass()">{{ visibleLabel() }}</span>
      <span class="block h-1.5 overflow-hidden rounded-full bg-surface-muted" aria-hidden="true">
        <span
          class="block h-full rounded-full transition-[width] duration-150 ease-out"
          [class]="fillClass()"
          [style.width.%]="percentTaken()"
        ></span>
      </span>
    </div>
  `,
})
export class SeatMeter {
  readonly seatsLeft = input.required<number>();
  readonly capacity = input.required<number>();

  protected readonly total = computed(() => Math.max(0, this.capacity()));
  protected readonly left = computed(() => Math.min(Math.max(0, this.seatsLeft()), this.total()));
  protected readonly tone = computed(() => seatTone(this.left()));
  protected readonly visibleLabel = computed(() => `${this.left()} of ${this.total()} left`);
  protected readonly accessibleLabel = computed(() =>
    this.left() === 0
      ? `No seats left, 0 of ${this.total()}`
      : `${this.left()} of ${this.total()} seats left`,
  );
  protected readonly percentTaken = computed(() =>
    this.total() === 0 ? 100 : Math.round(((this.total() - this.left()) / this.total()) * 100),
  );
  protected readonly fillClass = computed(() => FILL_CLASSES[this.tone()]);
  protected readonly textClass = computed(() => TEXT_CLASSES[this.tone()]);
}
