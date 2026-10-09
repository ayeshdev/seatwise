import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  untracked,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Button } from './button';
import { ConfirmDialogService } from './confirm-dialog.service';

/** Mount once (the app shell does). Uses the native `<dialog>`, so focus trapping and Escape just work. */
@Component({
  selector: 'sw-confirm-dialog',
  imports: [ReactiveFormsModule, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="sw-dialog"
      aria-labelledby="sw-confirm-title"
      aria-describedby="sw-confirm-message"
      (close)="onClosed()"
    >
      @if (service.pending(); as pending) {
        <form class="flex flex-col gap-4 p-6" (submit)="confirm($event)">
          <h2 id="sw-confirm-title" class="text-xl">{{ pending.options.title }}</h2>
          <p id="sw-confirm-message" class="text-ink-muted">{{ pending.options.message }}</p>
          @if (pending.options.askReason) {
            <div>
              <label class="mb-1 block text-sm font-medium" for="sw-confirm-reason">
                {{ pending.options.reasonLabel ?? 'Reason (optional)' }}
              </label>
              <textarea
                id="sw-confirm-reason"
                class="block w-full border-line bg-surface text-ink"
                rows="3"
                maxlength="500"
                [formControl]="reason"
              ></textarea>
            </div>
          }
          <div class="mt-2 flex justify-end gap-2">
            <sw-button (click)="cancel()">{{ pending.options.cancelLabel ?? 'Cancel' }}</sw-button>
            <sw-button type="submit" [variant]="pending.options.danger ? 'danger' : 'primary'">
              {{ pending.options.confirmLabel }}
            </sw-button>
          </div>
        </form>
      }
    </dialog>
  `,
})
export class ConfirmDialog {
  protected readonly service = inject(ConfirmDialogService);
  protected readonly reason = new FormControl('', { nonNullable: true });
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    // A click on the dialog box itself (not its content) is a click on the backdrop: cancel.
    // Added in code because the dialog is not an interactive control for keyboard users;
    // Escape already covers them.
    afterNextRender(() => {
      const el = this.dialog().nativeElement;
      el.addEventListener('click', (event) => {
        if (event.target === el) {
          this.service.answer(false);
        }
      });
    });

    effect(() => {
      const pending = this.service.pending();
      untracked(() => {
        const el = this.dialog().nativeElement;
        if (pending !== null) {
          this.reason.reset('');
          this.open(el);
        } else if (el.open) {
          this.close(el);
        }
      });
    });
  }

  protected confirm(event: Event): void {
    event.preventDefault();
    this.service.answer(true, this.reason.value);
  }

  protected cancel(): void {
    this.service.answer(false);
  }

  /** Escape closes the native dialog; treat that as "cancel". */
  protected onClosed(): void {
    this.service.answer(false);
  }

  // Older engines (and jsdom in unit tests) lack showModal/close; fall back to the `open` attribute.
  private close(el: HTMLDialogElement): void {
    if (typeof el.close === 'function') {
      el.close();
    } else {
      el.removeAttribute('open');
    }
  }

  private open(el: HTMLDialogElement): void {
    if (el.open) {
      return;
    }
    if (typeof el.showModal === 'function') {
      el.showModal();
    } else {
      el.setAttribute('open', '');
    }
  }
}
