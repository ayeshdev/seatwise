import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { Role, landingPathFor } from './role';
import { SessionStore } from './session.store';

/**
 * Lets a route through only for the given roles; anyone else is sent to their own landing page.
 * This is for clarity, not security: the API refuses disallowed calls regardless.
 */
export function roleGuard(...allowed: Role[]): CanMatchFn {
  return async () => {
    const session = inject(SessionStore);
    const router = inject(Router);

    await session.load();
    const role = session.role();
    if (role === null) {
      // Load failed or the account is deactivated: `App` shows a message instead of any route,
      // so let navigation settle quietly rather than raise a "cannot match" error.
      return true;
    }
    return allowed.includes(role) ? true : router.parseUrl(landingPathFor(role));
  };
}

/** The empty path: send each role to where it starts. */
export const landingRedirect: CanActivateFn = async () => {
  const session = inject(SessionStore);
  const router = inject(Router);

  await session.load();
  const role = session.role();
  return role === null ? true : router.parseUrl(landingPathFor(role));
};
