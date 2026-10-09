import { Injectable, signal } from '@angular/core';

export type ToastTone = 'info' | 'success' | 'error';

export interface Toast {
  id: number;
  message: string;
  tone: ToastTone;
}

const DEFAULT_DURATION_MS: Record<ToastTone, number> = {
  info: 5000,
  success: 5000,
  error: 9000,
};

/** Short, non-blocking messages. Rendered by `ToastHost`, announced politely to screen readers. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private readonly state = signal<readonly Toast[]>([]);

  readonly toasts = this.state.asReadonly();

  show(message: string, tone: ToastTone = 'info', durationMs = DEFAULT_DURATION_MS[tone]): void {
    // A burst of identical failures (e.g. polling while offline) should show one toast.
    if (this.state().some((t) => t.message === message && t.tone === tone)) {
      return;
    }
    const id = this.nextId++;
    this.state.update((list) => [...list, { id, message, tone }]);
    if (durationMs > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), durationMs),
      );
    }
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.state.update((list) => list.filter((t) => t.id !== id));
  }
}
