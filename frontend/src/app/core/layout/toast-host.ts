import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ToastService, ToastTone } from './toast.service';

const TONE_CLASSES: Record<ToastTone, string> = {
  info: 'border-line',
  success: 'border-ok',
  error: 'border-full',
};

@Component({
  selector: 'sw-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end sm:p-6"
      role="status"
      aria-live="polite"
      aria-atomic="false"
    >
      @for (toast of toasts.toasts(); track toast.id) {
        <div
          class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-card border bg-surface px-4 py-3 text-sm text-ink shadow-dialog"
          [class]="toneClasses[toast.tone]"
        >
          <p class="flex-1">{{ toast.message }}</p>
          <button
            type="button"
            class="-m-1 rounded-button p-1 text-ink-muted hover:text-ink"
            aria-label="Dismiss message"
            (click)="toasts.dismiss(toast.id)"
          >
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastHost {
  protected readonly toasts = inject(ToastService);
  protected readonly toneClasses = TONE_CLASSES;
}
