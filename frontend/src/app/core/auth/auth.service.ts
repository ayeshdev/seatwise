import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';

/** Thin wrapper over the Keycloak client so the rest of the app (and its tests) never touch it. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(Keycloak);

  /** Sends the browser to the sign-in page, then back to where the person was. */
  login(): Promise<void> {
    return this.keycloak.login({ redirectUri: window.location.href });
  }

  /** Ends the single sign-on session and returns to the app. */
  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
