import { Routes } from '@angular/router';

import { roleGuard } from '@core/auth/guards';

/** Children of `/workshops`. The parent route already limits access to Manager and Staff. */
export const WORKSHOP_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Workshops',
    loadComponent: () => import('./workshops-page').then((m) => m.WorkshopsPage),
  },
  // Listed before ':id' so "new" is not read as an id. Managers only; Staff are sent to their landing page.
  {
    path: 'new',
    title: 'Schedule a workshop',
    canMatch: [roleGuard('MANAGER')],
    loadComponent: () => import('./workshop-form-page').then((m) => m.WorkshopFormPage),
  },
  {
    path: ':id/edit',
    title: 'Edit workshop',
    canMatch: [roleGuard('MANAGER')],
    loadComponent: () => import('./workshop-form-page').then((m) => m.WorkshopFormPage),
  },
  {
    path: ':id',
    title: 'Workshop',
    loadComponent: () => import('./workshop-detail-page').then((m) => m.WorkshopDetailPage),
  },
];
