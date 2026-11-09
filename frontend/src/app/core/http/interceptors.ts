import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, retry, switchMap, throwError, timer } from 'rxjs';
import { AuthStore } from '../auth/auth-store';
import { API_BASE_URL } from '../config/app-config';
import { toApiError } from './api-error';

/**
 * Interceptors are functions that every HttpClient request passes through, like a pipeline.
 * They are registered once in app.config.ts, in this order:
 *
 *   errorInterceptor  ->  authInterceptor  ->  retryInterceptor  ->  network
 *
 * The first one listed is the outermost, so errorInterceptor sees the final result after retries.
 */

/** Is this request going to OUR backend (and not to some third-party site)? */
export function isApiRequest(url: string, apiBaseUrl: string): boolean {
  if (apiBaseUrl === '') {
    // Same-origin setup: API URLs are relative paths like "/api/bookings".
    // "//evil.example" is protocol-relative, i.e. ANOTHER site, so it must not match.
    return url.startsWith('/') && !url.startsWith('//');
  }
  return url === apiBaseUrl || url.startsWith(`${apiBaseUrl}/`);
}

/**
 * Adds "Authorization: Bearer <token>" to requests for our API when the user is signed in.
 * The token is never attached to any other host, so it can't leak to third parties.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthStore).token();
  if (!token || !isApiRequest(request.url, inject(API_BASE_URL))) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/** Statuses worth retrying: no connection, or the gateway briefly can't reach a service. */
const TRANSIENT_STATUSES = new Set([0, 502, 503, 504]);

/**
 * Retries failed GET requests up to 2 times, waiting 0.5 s and then 1 s.
 *
 * Only GET is retried because reading is safe to repeat. Repeating a POST could, for example,
 * create two bookings, so writes are never retried automatically.
 */
export const retryInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method !== 'GET') {
    return next(request);
  }
  return next(request).pipe(
    retry({
      count: 2,
      delay: (error: unknown, attempt: number) => {
        const isTransient =
          error instanceof HttpErrorResponse && TRANSIENT_STATUSES.has(error.status);
        return isTransient ? timer(500 * attempt) : throwError(() => error);
      },
    }),
  );
};

/**
 * Turns every HTTP failure into an ApiError (see api-error.ts) with a message that is safe to show.
 * If the server says our token is no longer valid (401), it first renews the session with the refresh
 * token and repeats the request once. Only if that fails does it sign out and open the sign-in page,
 * which brings the customer back to where they were afterwards.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const apiBaseUrl = inject(API_BASE_URL);

  // Note: this interceptor runs BEFORE authInterceptor adds the token, so `request` here never
  // has an Authorization header. We decide by URL instead.
  // A 401 from /auth/login just means "wrong password": the login form handles that itself.
  const isSignInCall = request.url.startsWith(`${apiBaseUrl}/auth/`);

  const signOut = () => {
    auth.expire();
    const returnUrl = router.url.startsWith('/login') ? '/' : router.url;
    void router.navigate(['/login'], { queryParams: { returnUrl, reason: 'expired' } });
  };

  return next(request).pipe(
    catchError((error: unknown) => {
      const apiError = toApiError(error);
      if (apiError.status !== 401 || isSignInCall || !isApiRequest(request.url, apiBaseUrl)) {
        return throwError(() => apiError);
      }
      if (!auth.canRefresh()) {
        signOut();
        return throwError(() => apiError);
      }
      // Renew once, then repeat the request (it passes authInterceptor again and picks up the new token).
      return auth.refresh().pipe(
        catchError(() => {
          signOut();
          return throwError(() => apiError);
        }),
        switchMap(() => next(request).pipe(catchError((retryError: unknown) => throwError(() => toApiError(retryError))))),
      );
    }),
  );
};
