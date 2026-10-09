import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Route,
  Router,
  RouterStateSnapshot,
  UrlSegment,
  UrlTree,
} from '@angular/router';

import { landingRedirect, roleGuard } from './guards';
import { Role } from './role';
import { SessionStore } from './session.store';

class FakeSession {
  readonly role = signal<Role | null>(null);
  load = jest.fn(async () => undefined);
}

describe('auth guards', () => {
  let session: FakeSession;
  let router: Router;

  beforeEach(() => {
    session = new FakeSession();
    TestBed.configureTestingModule({
      providers: [{ provide: SessionStore, useValue: session }],
    });
    router = TestBed.inject(Router);
  });

  const runMatch = (guard: ReturnType<typeof roleGuard>) =>
    TestBed.runInInjectionContext(() => guard({} as Route, [] as UrlSegment[])) as Promise<
      boolean | UrlTree
    >;

  const runLanding = () =>
    TestBed.runInInjectionContext(() =>
      landingRedirect({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;

  describe('roleGuard', () => {
    it('lets an allowed role through', async () => {
      session.role.set('MANAGER');

      await expect(runMatch(roleGuard('MANAGER', 'STAFF'))).resolves.toBe(true);
    });

    it('sends a denied role to its own landing page', async () => {
      session.role.set('STAFF');

      const result = await runMatch(roleGuard('ADMIN'));

      expect(result).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(result as UrlTree)).toBe('/workshops');
    });

    it('sends a denied admin to staff accounts', async () => {
      session.role.set('ADMIN');

      const result = await runMatch(roleGuard('MANAGER', 'STAFF'));

      expect(router.serializeUrl(result as UrlTree)).toBe('/staff-accounts');
    });

    it('waits for the session before deciding', async () => {
      let finishLoading: () => void = () => undefined;
      session.load.mockImplementation(
        () =>
          new Promise<undefined>((resolve) => {
            finishLoading = () => {
              session.role.set('ADMIN');
              resolve(undefined);
            };
          }),
      );

      let settled = false;
      const pending = runMatch(roleGuard('ADMIN')).then((value) => {
        settled = true;
        return value;
      });
      await Promise.resolve();
      expect(settled).toBe(false);

      finishLoading();
      await expect(pending).resolves.toBe(true);
    });

    it('lets navigation settle when the profile could not be loaded', async () => {
      await expect(runMatch(roleGuard('ADMIN'))).resolves.toBe(true);
    });
  });

  describe('landingRedirect', () => {
    it.each([
      ['ADMIN', '/staff-accounts'],
      ['MANAGER', '/workshops'],
      ['STAFF', '/workshops'],
    ] as const)('%s lands on %s', async (role, path) => {
      session.role.set(role);

      const result = await runLanding();

      expect(router.serializeUrl(result as UrlTree)).toBe(path);
    });
  });
});
