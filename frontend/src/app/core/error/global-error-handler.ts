import { ErrorHandler, Injectable, isDevMode } from '@angular/core';
import { ApiError } from '../http/api-error';
import { sessionStore } from '../util/browser-storage';

const RELOAD_KEY = 'baldev.chunk-reload';

/**
 * Catches errors nothing else handled (bugs, failed lazy-loaded chunks...).
 *
 * In production this is where you would report to a monitoring service (Sentry, Datadog, etc.).
 * We only log to the console for now; the hook point is ready.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    // Expected API failures are already shown to the user by the page that made the call.
    if (error instanceof ApiError) {
      return;
    }

    // After a new release, an open tab may ask for an old lazy-loaded file that no longer exists.
    // Reloading fetches the new version of the app. We reload at most once a minute, so a
    // genuinely broken deployment can't trap the user in an endless reload loop.
    const message = error instanceof Error ? error.message : String(error);
    if (
      /Failed to fetch dynamically imported module|Importing a module script failed/i.test(message)
    ) {
      const lastReload = Number(sessionStore.get(RELOAD_KEY) ?? 0);
      if (Date.now() - lastReload > 60_000) {
        sessionStore.set(RELOAD_KEY, String(Date.now()));
        window.location.reload();
        return;
      }
    }

    if (isDevMode()) {
      console.error(error);
    } else {
      // TODO(monitoring): send to your error-tracking service here.
      console.error('Unexpected error', message);
    }
  }
}
