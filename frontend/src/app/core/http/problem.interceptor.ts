import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '@core/auth/auth.service';
import { SessionStore } from '@core/auth/session.store';
import { ToastService } from '@core/layout/toast.service';

import { messageFor } from './messages';
import { toProblemError } from './problem';

/**
 * Turns every failed HTTP call into a typed `ProblemError` for the caller to catch.
 *
 * - Network errors and 5xx raise a global toast (the caller can't do much about them).
 * - 401 sends the person back to sign in.
 * - A deactivated account switches the app to the "deactivated" page.
 * - 400 and 409 are left alone: the calling component shows them next to the thing that failed.
 */
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const toasts = inject(ToastService);
  const auth = inject(AuthService);
  const session = inject(SessionStore);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const problem = toProblemError(error);

      if (problem.status === 401) {
        void auth.login();
      } else if (problem.code === 'ACCOUNT_INACTIVE') {
        session.markDeactivated();
      } else if (problem.isNetworkError || problem.isServerError) {
        toasts.error(messageFor(problem));
      }
      return throwError(() => problem);
    }),
  );
};
