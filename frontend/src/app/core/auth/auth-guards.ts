import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth-store';

/**
 * Route guards decide whether a page may open. They are a UX feature, not security: the API
 * gateway checks the token on every request, so hiding a page in the browser protects nothing
 * by itself. Guards just send people to the right place.
 */

/** Pages that need an account (checkout, my trips). Sends guests to sign-in, then back here. */
export const requireSignIn: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (auth.isSignedIn()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The sign-in and sign-up pages make no sense when already signed in. */
export const guestOnly: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isSignedIn() ? router.createUrlTree(['/trips']) : true;
};
