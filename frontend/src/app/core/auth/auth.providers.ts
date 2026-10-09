import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';
import {
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  provideKeycloak,
  withAutoRefreshToken,
} from 'keycloak-angular';

import { RuntimeConfig } from '@core/config/runtime-config';

/** Idle limit before re-prompting for sign-in. Kept just under the 30 minute refresh-token idle. */
const IDLE_TIMEOUT_MS = 25 * 60 * 1000;

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Bearer token goes only to our own API, never to other origins. */
export function apiBearerCondition(apiBase: string): { urlPattern: RegExp } {
  return { urlPattern: new RegExp(`^${escapeRegExp(apiBase.replace(/\/+$/, ''))}/`) };
}

/**
 * Keycloak login-required with PKCE, silent token refresh, and bearer tokens on API calls.
 * Pair with `includeBearerTokenInterceptor` in `provideHttpClient(withInterceptors([...]))`.
 */
export function provideAuth(config: RuntimeConfig): EnvironmentProviders {
  const bearer: Provider = {
    provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
    useValue: [apiBearerCondition(config.apiBase)],
  };
  return makeEnvironmentProviders([
    provideKeycloak({
      config: { url: config.idpUrl, realm: config.realm, clientId: config.clientId },
      initOptions: { onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false },
      features: [withAutoRefreshToken({ sessionTimeout: IDLE_TIMEOUT_MS, onInactivityTimeout: 'login' })],
    }),
    bearer,
  ]);
}
