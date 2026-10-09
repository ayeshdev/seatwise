import { Routes } from '@angular/router';

/** Mounted at `/staff-accounts` behind the Admin guard (see `app.routes.ts`). `new` must precede `:id`. */
export const STAFF_ACCOUNT_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Staff accounts',
    loadComponent: () => import('./staff-accounts-page').then((m) => m.StaffAccountsPage),
  },
  {
    path: 'new',
    title: 'Add staff member',
    loadComponent: () =>
      import('./staff-account-create-page').then((m) => m.StaffAccountCreatePage),
  },
  {
    path: ':id',
    title: 'Staff account',
    loadComponent: () =>
      import('./staff-account-detail-page').then((m) => m.StaffAccountDetailPage),
  },
];
