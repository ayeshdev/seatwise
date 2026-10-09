import { bootstrapApplication } from '@angular/platform-browser';

import { loadRuntimeConfig } from '@core/config/runtime-config';

import { App } from './app/app';
import { createAppConfig } from './app/app.config';

/** Plain DOM, because Angular isn't running yet. */
function showStartupError(message: string): void {
  const box = document.createElement('main');
  box.setAttribute('role', 'alert');
  box.style.cssText = 'max-width:32rem;margin:20vh auto;padding:0 1.5rem;font-family:system-ui,sans-serif';
  const heading = document.createElement('h1');
  heading.textContent = "We couldn't start Seatwise";
  const body = document.createElement('p');
  body.textContent = message;
  box.append(heading, body);
  document.body.replaceChildren(box);
}

// Config comes first: Keycloak needs the identity-provider URL when its providers are created.
loadRuntimeConfig()
  .then((config) => bootstrapApplication(App, createAppConfig(config)))
  .catch((err: unknown) => {
    console.error(err);
    showStartupError('Please check your connection and reload the page. If it keeps happening, tell an administrator.');
  });
