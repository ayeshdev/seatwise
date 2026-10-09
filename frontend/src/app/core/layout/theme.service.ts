import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

export const THEME_STORAGE_KEY = 'seatwise.theme';

const ORDER: readonly ThemeMode[] = ['light', 'dark', 'system'];

function isThemeMode(value: unknown): value is ThemeMode {
  return typeof value === 'string' && (ORDER as readonly string[]).includes(value);
}

/** Light / dark / follow-the-device, remembered per browser and applied as `data-theme` on `<html>`. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly state = signal<ThemeMode>(this.readStored());

  readonly mode = this.state.asReadonly();

  /** Applies the saved choice. Call once at startup. */
  init(): void {
    this.apply(this.state());
  }

  set(mode: ThemeMode): void {
    this.state.set(mode);
    this.apply(mode);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      // Storage can be blocked (private mode, policy). The choice still applies for this visit.
    }
  }

  /** Light -> dark -> system -> light. */
  cycle(): void {
    const next = ORDER[(ORDER.indexOf(this.state()) + 1) % ORDER.length];
    this.set(next);
  }

  private apply(mode: ThemeMode): void {
    const root = this.document.documentElement;
    if (mode === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', mode);
    }
  }

  private readStored(): ThemeMode {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      return isThemeMode(stored) ? stored : 'system';
    } catch {
      return 'system';
    }
  }
}
