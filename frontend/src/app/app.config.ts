import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { includeBearerTokenInterceptor } from 'keycloak-angular';

import { provideAuth } from '@core/auth/auth.providers';
import { RuntimeConfig, provideRuntimeConfig } from '@core/config/runtime-config';
import { problemInterceptor } from '@core/http/problem.interceptor';
import { ThemeService } from '@core/layout/theme.service';

import { routes } from './app.routes';

/**
 * Built after `/config.json` is loaded (see `main.ts`), because Keycloak needs its URL when its
 * providers are created. Keycloak's app initializer then signs the person in before the app starts.
 */
export function createAppConfig(config: RuntimeConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideZoneChangeDetection({ eventCoalescing: true }),
      provideRuntimeConfig(config),
      provideAuth(config),
      // Order matters: the problem interceptor wraps the bearer one so it sees the final error.
      provideHttpClient(withInterceptors([problemInterceptor, includeBearerTokenInterceptor])),
      provideRouter(routes),
      provideAppInitializer(() => inject(ThemeService).init()),
    ],
  };
}
