import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type SpinnerSize = 'sm' | 'md' | 'lg';

const SIZES: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-10 w-10 border-[3px]',
};

/** Pass `label=""` when a parent already announces the busy state (e.g. a loading button). */
@Component({
  selector: 'sw-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-center' },
  template: `
    <span
      class="inline-block rounded-full border-current border-t-transparent motion-safe:animate-spin"
      [class]="sizeClasses()"
      aria-hidden="true"
    ></span>
    @if (label()) {
      <span class="sr-only" role="status">{{ label() }}</span>
    }
  `,
})
export class Spinner {
  readonly size = input<SpinnerSize>('md');
  readonly label = input('Loading');

  protected readonly sizeClasses = computed(() => SIZES[this.size()]);
}
