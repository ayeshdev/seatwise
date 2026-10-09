import { Injectable, InjectionToken, Provider, inject } from '@angular/core';

/** Environment-specific settings, served as `/config.json` so one build runs everywhere. */
export interface RuntimeConfig {
  idpUrl: string;
  realm: string;
  clientId: string;
  apiBase: string;
}

export const RUNTIME_CONFIG_URL = '/config.json';

const REQUIRED_KEYS: readonly (keyof RuntimeConfig)[] = ['idpUrl', 'realm', 'clientId', 'apiBase'];

export function isRuntimeConfig(value: unknown): value is RuntimeConfig {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return REQUIRED_KEYS.every((key) => typeof record[key] === 'string' && record[key] !== '');
}

/**
 * Fetches and validates `/config.json`. Called from `main.ts` before the app bootstraps, because
 * Keycloak needs its URL at provider-creation time. Rejects if the file is missing or malformed.
 */
export async function loadRuntimeConfig(
  fetchFn: typeof fetch = (input, init) => fetch(input, init),
): Promise<RuntimeConfig> {
  const response = await fetchFn(RUNTIME_CONFIG_URL, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error('The application settings (config.json) could not be loaded.');
  }
  const raw: unknown = await response.json();
  if (!isRuntimeConfig(raw)) {
    throw new Error('The application settings (config.json) are missing or incomplete.');
  }
  return {
    idpUrl: raw.idpUrl,
    realm: raw.realm,
    clientId: raw.clientId,
    apiBase: raw.apiBase,
  };
}

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('RUNTIME_CONFIG');

/** Makes the already-loaded config available synchronously through DI. */
export function provideRuntimeConfig(config: RuntimeConfig): Provider {
  return { provide: RUNTIME_CONFIG, useValue: config };
}

@Injectable({ providedIn: 'root' })
export class RuntimeConfigStore {
  private readonly value = inject(RUNTIME_CONFIG, { optional: true });

  /** The loaded config, or null when none was provided. */
  readonly config: RuntimeConfig | null = this.value;

  get loaded(): boolean {
    return this.value !== null;
  }

  /** The loaded config. Throws if the app was started without `provideRuntimeConfig`. */
  require(): RuntimeConfig {
    if (this.value === null) {
      throw new Error('Runtime config was read before it was provided.');
    }
    return this.value;
  }

  /** Builds a backend URL, e.g. `apiUrl('/v1/me')` gives `/api/v1/me`. */
  apiUrl(path: string): string {
    const base = this.require().apiBase.replace(/\/+$/, '');
    return `${base}/${path.replace(/^\/+/, '')}`;
  }
}
