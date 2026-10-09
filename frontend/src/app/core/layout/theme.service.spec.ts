import { TestBed } from '@angular/core/testing';

import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  afterEach(() => jest.restoreAllMocks());

  it('defaults to following the device and sets no data-theme', () => {
    const theme = TestBed.inject(ThemeService);
    theme.init();

    expect(theme.mode()).toBe('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('applies and remembers a choice', () => {
    const theme = TestBed.inject(ThemeService);

    theme.set('dark');

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('restores the saved choice on start', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    const theme = TestBed.inject(ThemeService);
    theme.init();

    expect(theme.mode()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('cycles light, dark, system', () => {
    const theme = TestBed.inject(ThemeService);
    theme.set('light');

    theme.cycle();
    expect(theme.mode()).toBe('dark');
    theme.cycle();
    expect(theme.mode()).toBe('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    theme.cycle();
    expect(theme.mode()).toBe('light');
  });

  it('still works when storage is blocked', () => {
    jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    const theme = TestBed.inject(ThemeService);
    expect(theme.mode()).toBe('system');
    expect(() => theme.set('dark')).not.toThrow();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
