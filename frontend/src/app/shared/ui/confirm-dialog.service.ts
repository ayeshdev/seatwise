import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  /** Styles the confirm button as destructive. */
  danger?: boolean;
  /** Adds an optional "reason" textarea. */
  askReason?: boolean;
  reasonLabel?: string;
}

export interface ConfirmResult {
  confirmed: boolean;
  /** Trimmed reason, or null when none was asked for or left blank. */
  reason: string | null;
}

export interface PendingConfirm {
  options: Required<Pick<ConfirmOptions, 'title' | 'message' | 'confirmLabel'>> & ConfirmOptions;
  resolve: (result: ConfirmResult) => void;
}

/**
 * Asks "are you sure?" and resolves when the person answers. Cancelling (button, Escape or a
 * click outside) resolves with `confirmed: false`; it never rejects. Rendered by `ConfirmDialog`.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly state = signal<PendingConfirm | null>(null);

  readonly pending = this.state.asReadonly();

  confirm(options: ConfirmOptions): Promise<ConfirmResult> {
    // Only one question at a time: a newer one cancels the older.
    this.state()?.resolve({ confirmed: false, reason: null });
    return new Promise<ConfirmResult>((resolve) => {
      this.state.set({ options, resolve });
    });
  }

  /** Called by the dialog component. */
  answer(confirmed: boolean, reason: string | null = null): void {
    const current = this.state();
    if (current === null) {
      return;
    }
    this.state.set(null);
    const trimmed = reason?.trim() ?? '';
    current.resolve({
      confirmed,
      reason: confirmed && current.options.askReason && trimmed !== '' ? trimmed : null,
    });
  }
}
