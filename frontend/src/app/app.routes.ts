import { Routes } from '@angular/router';
import { guestOnly, requireSignIn } from './core/auth/auth-guards';

/**
 * Every page is lazy-loaded (`loadComponent`): its code is downloaded only when someone opens it.
 * The first visit stays fast, and the router preloads the rest in the background afterwards
 * (see `withPreloading` in app.config.ts).
 *
 * `title` sets the browser tab title (see PageTitleStrategy).
 * `canActivate` runs guards before the page opens (see auth-guards.ts).
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    path: 'flights',
    title: 'Choose your flight',
    loadComponent: () =>
      import('./features/flight-search/search-results-page').then((m) => m.SearchResultsPage),
  },
  {
    path: 'book/:flightInstanceId',
    title: 'Book your trip',
    canActivate: [requireSignIn],
    loadComponent: () => import('./features/checkout/checkout-page').then((m) => m.CheckoutPage),
  },
  {
    // Razorpay (and the offline stub) send the customer back here after paying.
    // The path is set by payment-service (RazorpayGateway), so it must not change.
    path: 'booking-success/:bookingId',
    title: 'Confirming your booking',
    canActivate: [requireSignIn],
    loadComponent: () =>
      import('./features/payment/payment-return-page').then((m) => m.PaymentReturnPage),
  },
  {
    path: 'trips',
    title: 'My trips',
    canActivate: [requireSignIn],
    loadComponent: () => import('./features/trips/trips-page').then((m) => m.TripsPage),
  },
  {
    path: 'trips/:bookingId',
    title: 'Trip details',
    canActivate: [requireSignIn],
    loadComponent: () => import('./features/trips/trip-detail-page').then((m) => m.TripDetailPage),
  },
  {
    path: 'account',
    title: 'My account',
    canActivate: [requireSignIn],
    loadComponent: () => import('./features/account/account-page').then((m) => m.AccountPage),
  },
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestOnly],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'signup',
    title: 'Create your account',
    canActivate: [guestOnly],
    loadComponent: () => import('./features/auth/signup-page').then((m) => m.SignupPage),
  },
  {
    path: 'help',
    title: 'Help and travel information',
    loadComponent: () => import('./features/help/help-page').then((m) => m.HelpPage),
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
  },
];
