import { TestBed } from '@angular/core/testing';

import {
  RUNTIME_CONFIG_URL,
  RuntimeConfig,
  RuntimeConfigStore,
  isRuntimeConfig,
  loadRuntimeConfig,
  provideRuntimeConfig,
} from './runtime-config';

const VALID: RuntimeConfig = {
  idpUrl: 'http://localhost:8281',
  realm: 'seatwise',
  clientId: 'seatwise-desk',
  apiBase: '/api',
};

function fakeFetch(body: unknown, ok = true): typeof fetch {
  return jest.fn(async () => ({ ok, json: async () => body }) as Response);
}

describe('RuntimeConfigStore', () => {
  it('refuses reads when no config was provided', () => {
    TestBed.configureTestingModule({});
    const store = TestBed.inject(RuntimeConfigStore);

    expect(store.config).toBeNull();
    expect(store.loaded).toBe(false);
    expect(() => store.require()).toThrow();
  });

  it('exposes the provided config synchronously', () => {
    TestBed.configureTestingModule({ providers: [provideRuntimeConfig(VALID)] });
    const store = TestBed.inject(RuntimeConfigStore);

    expect(store.config).toEqual(VALID);
    expect(store.loaded).toBe(true);
    expect(store.require().apiBase).toBe('/api');
  });

  it('builds backend urls from apiBase', () => {
    TestBed.configureTestingModule({
      providers: [provideRuntimeConfig({ ...VALID, apiBase: '/api/' })],
    });

    expect(TestBed.inject(RuntimeConfigStore).apiUrl('/v1/me')).toBe('/api/v1/me');
  });
});

describe('loadRuntimeConfig', () => {
  it('loads /config.json', async () => {
    const fetchFn = fakeFetch(VALID);

    await expect(loadRuntimeConfig(fetchFn)).resolves.toEqual(VALID);
    expect(fetchFn).toHaveBeenCalledWith(RUNTIME_CONFIG_URL, expect.anything());
  });

  it('rejects an incomplete config', async () => {
    await expect(loadRuntimeConfig(fakeFetch({ idpUrl: 'http://localhost:8281' }))).rejects.toThrow(
      /config\.json/,
    );
  });

  it('rejects when the file cannot be fetched', async () => {
    await expect(loadRuntimeConfig(fakeFetch({}, false))).rejects.toThrow(/config\.json/);
  });
});

describe('isRuntimeConfig', () => {
  it('accepts a complete config', () => {
    expect(isRuntimeConfig(VALID)).toBe(true);
  });

  it.each([null, undefined, 'x', 42, {}, { ...VALID, realm: '' }, { ...VALID, apiBase: 1 }])(
    'rejects %p',
    (value) => {
      expect(isRuntimeConfig(value)).toBe(false);
    },
  );
});
