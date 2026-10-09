import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** A calm "nothing here" panel. Project an action (e.g. a button) as content. */
@Component({
  selector: 'sw-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-col items-center gap-3 rounded-card border border-dashed border-line bg-surface px-6 py-12 text-center"
    >
      <h2 class="text-xl">{{ heading() }}</h2>
      @if (description()) {
        <p class="max-w-prose text-ink-muted">{{ description() }}</p>
      }
      <div class="mt-2 empty:hidden">
        <ng-content />
      </div>
    </div>
  `,
})
export class EmptyState {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
}
