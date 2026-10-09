import { TestBed } from '@angular/core/testing';

import { ToastService } from './toast.service';

describe('ToastService', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('shows a toast and removes it after its duration', () => {
    const toasts = TestBed.inject(ToastService);

    toasts.success('Saved');
    expect(toasts.toasts().map((t) => t.message)).toEqual(['Saved']);

    jest.advanceTimersByTime(5001);
    expect(toasts.toasts()).toHaveLength(0);
  });

  it('shows one toast for a burst of identical messages', () => {
    const toasts = TestBed.inject(ToastService);

    toasts.error('Offline');
    toasts.error('Offline');

    expect(toasts.toasts()).toHaveLength(1);
  });

  it('dismisses on request', () => {
    const toasts = TestBed.inject(ToastService);
    toasts.show('Hello');

    toasts.dismiss(toasts.toasts()[0].id);

    expect(toasts.toasts()).toHaveLength(0);
  });
});
