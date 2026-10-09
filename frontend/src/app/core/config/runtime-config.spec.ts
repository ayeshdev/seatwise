import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RUNTIME_CONFIG_URL, RuntimeConfig, RuntimeConfigStore, isRuntimeConfig } from './runtime-config';

const VALID: RuntimeConfig = {
  idpUrl: 'http://localhost:8281',
  realm: 'seatwise',
  clientId: 'seatwise-desk',
  apiBase: '/api',
};

describe('RuntimeConfigStore', () => {
  let store: RuntimeConfigStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(RuntimeConfigStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts empty and refuses reads before loading', () => {
    expect(store.config()).toBeNull();
    expect(store.loaded()).toBe(false);
    expect(() => store.require()).toThrow();
  });

  it('loads /config.json into the store', async () => {
    const pending = store.load();
    http.expectOne(RUNTIME_CONFIG_URL).flush(VALID);

    await expect(pending).resolves.toEqual(VALID);
    expect(store.config()).toEqual(VALID);
    expect(store.loaded()).toBe(true);
    expect(store.require().apiBase).toBe('/api');
  });

  it('rejects an incomplete config and stays empty', async () => {
    const pending = store.load();
    http.expectOne(RUNTIME_CONFIG_URL).flush({ idpUrl: 'http://localhost:8281' });

    await expect(pending).rejects.toThrow(/config\.json/);
    expect(store.config()).toBeNull();
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
