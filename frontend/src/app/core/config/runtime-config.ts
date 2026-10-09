import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

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

@Injectable({ providedIn: 'root' })
export class RuntimeConfigStore {
  private readonly http = inject(HttpClient);
  private readonly state = signal<RuntimeConfig | null>(null);

  readonly config = this.state.asReadonly();
  readonly loaded = computed(() => this.state() !== null);

  /** Fetches `/config.json` and stores it. Rejects if the file is missing or malformed. */
  async load(): Promise<RuntimeConfig> {
    const raw: unknown = await firstValueFrom(this.http.get<unknown>(RUNTIME_CONFIG_URL));
    if (!isRuntimeConfig(raw)) {
      throw new Error('The application settings (config.json) are missing or incomplete.');
    }
    const config: RuntimeConfig = {
      idpUrl: raw.idpUrl,
      realm: raw.realm,
      clientId: raw.clientId,
      apiBase: raw.apiBase,
    };
    this.state.set(config);
    return config;
  }

  /** The loaded config. Throws if read before the app initializer has finished. */
  require(): RuntimeConfig {
    const config = this.state();
    if (config === null) {
      throw new Error('Runtime config was read before it finished loading.');
    }
    return config;
  }
}
