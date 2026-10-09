import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type WorkshopStatus = 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type RegistrationStatus = 'ACTIVE' | 'WAITLISTED' | 'CANCELLED';
export type Status = WorkshopStatus | RegistrationStatus;

type Tone = 'ok' | 'full' | 'warn' | 'accent' | 'quiet';

const LABELS: Record<Status, string> = {
  OPEN: 'Open',
  FULL: 'Full',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  ACTIVE: 'Registered',
  WAITLISTED: 'Waitlisted',
};

const TONES: Record<Status, Tone> = {
  OPEN: 'ok',
  FULL: 'full',
  IN_PROGRESS: 'accent',
  COMPLETED: 'quiet',
  CANCELLED: 'quiet',
  ACTIVE: 'ok',
  WAITLISTED: 'warn',
};

// Full class names so Tailwind can see them. Text stays `ink` for contrast; colour is the dot and border.
const TONE_CLASSES: Record<Tone, { chip: string; dot: string }> = {
  ok: { chip: 'border-ok/50 bg-ok/10', dot: 'bg-ok' },
  full: { chip: 'border-full/50 bg-full/10', dot: 'bg-full' },
  warn: { chip: 'border-warn/50 bg-warn/10', dot: 'bg-warn' },
  accent: { chip: 'border-accent/50 bg-accent-soft', dot: 'bg-accent' },
  quiet: { chip: 'border-quiet/50 bg-quiet/10', dot: 'bg-quiet' },
};

/** The words people see for a status (never the raw enum). */
export function statusLabel(status: Status): string {
  return LABELS[status];
}

/** Always text plus colour, never colour alone. */
@Component({
  selector: 'sw-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-button border px-2 py-0.5 text-xs font-medium text-ink"
      [class]="tone().chip"
    >
      <span class="h-1.5 w-1.5 rounded-full" [class]="tone().dot" aria-hidden="true"></span>
      {{ label() }}
    </span>
  `,
})
export class StatusBadge {
  readonly status = input.required<Status>();

  protected readonly label = computed(() => statusLabel(this.status()));
  protected readonly tone = computed(() => TONE_CLASSES[TONES[this.status()]]);
}
