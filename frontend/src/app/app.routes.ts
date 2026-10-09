import { Routes } from '@angular/router';

import { landingRedirect, roleGuard } from '@core/auth/guards';

export const routes: Routes = [
  // Landing: each role goes to where it starts.
  { path: '', pathMatch: 'full', canActivate: [landingRedirect], children: [] },
  {
    path: 'workshops',
    title: 'Workshops',
    canMatch: [roleGuard('MANAGER', 'STAFF')],
    loadComponent: () => import('@features/workshops/workshops-page').then((m) => m.WorkshopsPage),
  },
  {
    path: 'activity',
    title: 'Activity',
    canMatch: [roleGuard('MANAGER', 'STAFF')],
    loadComponent: () => import('@features/activity/activity-page').then((m) => m.ActivityPage),
  },
  {
    path: 'staff-accounts',
    canMatch: [roleGuard('ADMIN')],
    loadChildren: () =>
      import('@features/staff-accounts/staff-accounts.routes').then((m) => m.STAFF_ACCOUNT_ROUTES),
  },
  {
    path: 'account-activity',
    title: 'Account activity',
    canMatch: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('@features/activity/account-activity-page').then((m) => m.AccountActivityPage),
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('@core/layout/not-found').then((m) => m.NotFound),
  },
];
