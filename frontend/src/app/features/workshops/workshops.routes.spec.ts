import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CanMatchFn, Route, Router, UrlSegment, UrlTree } from '@angular/router';

import { Role } from '@core/auth/role';
import { SessionStore } from '@core/auth/session.store';

import { WORKSHOP_ROUTES } from './workshops.routes';

describe('WORKSHOP_ROUTES', () => {
  const byPath = (path: string) => WORKSHOP_ROUTES.find((r) => r.path === path);

  function runGuard(route: Route | undefined, role: Role): Promise<boolean | UrlTree> {
    const guard = route?.canMatch?.[0] as CanMatchFn;
    TestBed.configureTestingModule({
      providers: [
        { provide: SessionStore, useValue: { role: signal(role), load: async () => undefined } },
      ],
    });
    return TestBed.runInInjectionContext(() => guard(route as Route, [] as UrlSegment[])) as Promise<
      boolean | UrlTree
    >;
  }

  it('keeps the create and edit forms to managers', async () => {
    for (const path of ['new', ':id/edit']) {
      expect(byPath(path)?.canMatch).toHaveLength(1);
    }

    const denied = await runGuard(byPath('new'), 'STAFF');
    expect(denied).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(denied as UrlTree)).toBe('/workshops');
  });

  it('lets a manager into the forms', async () => {
    await expect(runGuard(byPath(':id/edit'), 'MANAGER')).resolves.toBe(true);
  });

  it('puts "new" before ":id" so it is not read as an id', () => {
    const paths = WORKSHOP_ROUTES.map((r) => r.path);

    expect(paths.indexOf('new')).toBeLessThan(paths.indexOf(':id'));
  });

  it('leaves the list and the detail page to everyone the parent route allows', () => {
    expect(byPath('')?.canMatch).toBeUndefined();
    expect(byPath(':id')?.canMatch).toBeUndefined();
  });
});
