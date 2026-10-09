import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Spinner } from './spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md';

const BASE =
  'inline-flex w-full items-center justify-center gap-2 rounded-button border font-medium transition-colors duration-150 ease-out disabled:cursor-not-allowed disabled:opacity-60';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'border-accent bg-accent text-surface hover:bg-accent/90',
  secondary: 'border-line bg-surface text-ink hover:bg-surface-muted',
  ghost: 'border-transparent bg-transparent text-ink hover:bg-surface-muted',
  danger: 'border-full bg-surface text-full hover:bg-full hover:text-surface',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2',
};

/**
 * One primary button per view; everything else is secondary or ghost.
 * Click it like a normal element: `<sw-button (click)="save()">Save</sw-button>`.
 */
@Component({
  selector: 'sw-button',
  imports: [Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'inline-flex',
    '[class.pointer-events-none]': 'blocked()',
  },
  template: `
    <button
      [type]="type()"
      [class]="classes()"
      [disabled]="blocked()"
      [attr.aria-busy]="loading() ? 'true' : null"
      [attr.aria-label]="ariaLabel()"
    >
      @if (loading()) {
        <sw-spinner size="sm" label="" />
      }
      <ng-content />
    </button>
  `,
})
export class Button {
  readonly variant = input<ButtonVariant>('secondary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly ariaLabel = input<string | null>(null);

  protected readonly blocked = computed(() => this.disabled() || this.loading());
  protected readonly classes = computed(
    () => `${BASE} ${VARIANTS[this.variant()]} ${SIZES[this.size()]}`,
  );
}
