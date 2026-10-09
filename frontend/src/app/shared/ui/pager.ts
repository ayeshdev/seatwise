import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Button } from './button';

/** Previous / Next with a "21-40 of 87" summary. `page` is zero-based, like the API. */
@Component({
  selector: 'sw-pager',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <nav class="flex items-center justify-between gap-4" aria-label="Pagination">
      <p class="text-sm text-ink-muted tabular-nums" aria-live="polite">{{ summary() }}</p>
      <div class="flex gap-2">
        <sw-button size="sm" [disabled]="!hasPrevious()" (click)="go(page() - 1)">Previous</sw-button>
        <sw-button size="sm" [disabled]="!hasNext()" (click)="go(page() + 1)">Next</sw-button>
      </div>
    </nav>
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalItems = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly pageCount = computed(() =>
    this.size() > 0 ? Math.ceil(this.totalItems() / this.size()) : 0,
  );
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.pageCount());
  protected readonly summary = computed(() => {
    const total = this.totalItems();
    if (total === 0) {
      return 'No results';
    }
    const first = this.page() * this.size() + 1;
    const last = Math.min(total, (this.page() + 1) * this.size());
    return `${first}–${last} of ${total}`;
  });

  protected go(page: number): void {
    if (page >= 0 && page < this.pageCount()) {
      this.pageChange.emit(page);
    }
  }
}
