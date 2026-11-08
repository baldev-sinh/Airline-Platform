import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEnIn from '@angular/common/locales/en-IN';
import {
  ApplicationConfig,
  ErrorHandler,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  PreloadAllModules,
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withPreloading,
} from '@angular/router';
import { routes } from './app.routes';
import { GlobalErrorHandler } from './core/error/global-error-handler';
import { authInterceptor, errorInterceptor, retryInterceptor } from './core/http/interceptors';
import { PageTitleStrategy } from './core/layout/page-title-strategy';

// Indian English formatting: ₹5,300, 1,00,000 (lakh grouping), "20 Nov 2026".
registerLocaleData(localeEnIn);

/**
 * App-wide providers: the "wiring" of the application, read once at start-up.
 * Angular 22 apps are zoneless by default: the screen updates when signals change, not after
 * every browser event, which is faster and easier to reason about.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    { provide: LOCALE_ID, useValue: 'en-IN' },

    provideRouter(
      routes,
      // Route params and query params arrive as component inputs, e.g. `bookingId = input()`.
      withComponentInputBinding(),
      // Scroll to the top on navigation, and back to the old position on browser "Back".
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
      // After the first page is shown, download the other pages in the background.
      withPreloading(PreloadAllModules),
    ),
    { provide: TitleStrategy, useClass: PageTitleStrategy },

    // Requests use the browser's fetch() API: Angular's default, so no `withFetch()`.
    // (`withXhr()` would switch to XMLHttpRequest, needed only for upload progress bars.)
    provideHttpClient(
      // Order matters: the first is the outermost (see core/http/interceptors.ts).
      withInterceptors([errorInterceptor, authInterceptor, retryInterceptor]),
    ),
  ],
};
