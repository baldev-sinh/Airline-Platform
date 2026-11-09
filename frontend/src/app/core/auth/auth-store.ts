import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, firstValueFrom, from, map, of, shareReplay, tap, throwError } from 'rxjs';
import { AuthResponse, LoginRequest, SignupRequest, User } from '../models/api-models';
import { localStore } from '../util/browser-storage';
import { AuthApi } from './auth-api';
import { readTokenExpiry } from './jwt';

/** What we remember about a signed-in user. */
interface Session {
  readonly token: string;
  readonly user: User;
  /** Access token expiry, milliseconds since 1970 (same unit as Date.now()). */
  readonly expiresAt: number;
  /** Single-use refresh token; null when the backend did not issue one. */
  readonly refreshToken: string | null;
  /** Refresh token expiry (ms); after it the user must sign in again. */
  readonly refreshExpiresAt: number | null;
}

const STORAGE_KEY = 'baldev.session';
const REFRESH_LOCK = 'baldev.session.refresh';

/** Refresh this long before the access token runs out, so requests never race its expiry. */
const REFRESH_AHEAD_MS = 60_000;

/** Browsers can't schedule timers further than ~24.8 days ahead (2^31 - 1 ms). */
const MAX_TIMEOUT_MS = 2_147_483_647;

/**
 * The single source of truth for "who is signed in".
 *
 * State lives in a signal: components read `auth.user()` or `auth.isSignedIn()` and update
 * automatically when it changes. No manual subscriptions needed.
 *
 * Tokens: the access token lives 15 minutes; a refresh token (single-use, rotated by the server)
 * gets a new one shortly before it expires, and after a 401. Both are kept in localStorage so a page
 * reload does not sign the user out. The risk is limited by:
 *  - short access tokens, and refresh tokens the server rotates and revokes on reuse,
 *  - sending them only to our own API (see interceptors.ts),
 *  - a strict Content-Security-Policy that blocks injected scripts (see nginx/default.conf).
 *
 * Refreshing must never happen twice with the same refresh token: the server treats that as theft and
 * signs the user out everywhere. So refreshes are shared within a tab and serialized across tabs.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);

  private readonly session = signal<Session | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  private refreshInFlight: Observable<void> | null = null;

  /** The signed-in user, or null. */
  readonly user = computed(() => this.session()?.user ?? null);
  readonly isSignedIn = computed(() => this.session() !== null);
  /** True after the session ran out (not after a normal sign-out), so the UI can explain why. */
  readonly sessionExpired = signal(false);

  constructor() {
    this.restore();
    // If the user signs in, refreshes or signs out in another tab, follow along in this one.
    window.addEventListener('storage', (event) => {
      if (event.key === STORAGE_KEY) {
        this.restore();
      }
    });
  }

  /** The raw access token for the Authorization header, or null if signed out or expired. */
  token(): string | null {
    const current = this.session();
    if (!current || current.expiresAt <= Date.now()) {
      return null;
    }
    return current.token;
  }

  /** True while the session can still be renewed without asking for the password. */
  canRefresh(): boolean {
    const current = this.session();
    return (
      !!current?.refreshToken && (current.refreshExpiresAt === null || current.refreshExpiresAt > Date.now())
    );
  }

  login(credentials: LoginRequest): Observable<User> {
    return this.api.login(credentials).pipe(
      tap((response) => this.start(response)),
      map((response) => response.user),
    );
  }

  signup(details: SignupRequest): Observable<User> {
    return this.api.signup(details).pipe(
      tap((response) => this.start(response)),
      map((response) => response.user),
    );
  }

  /**
   * Gets a new access token with the refresh token. Concurrent callers share one request; another tab
   * that refreshed first is followed instead of refreshing again. Errors if the session cannot be renewed.
   */
  refresh(): Observable<void> {
    if (!this.refreshInFlight) {
      // The lock is held until the refresh request has completed, not just started.
      const done = this.withCrossTabLock(() => firstValueFrom(this.refreshOnce(), { defaultValue: undefined }));
      this.refreshInFlight = from(done).pipe(
        map(() => undefined),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }

  /** Normal sign-out from the menu: also revokes the session on the server. */
  logout(): void {
    const refreshToken = this.session()?.refreshToken ?? null;
    if (this.session()) {
      // Best effort: the local session ends either way.
      this.api.logout(refreshToken).subscribe({ error: () => undefined });
    }
    this.clear();
    this.sessionExpired.set(false);
  }

  /** The server rejected our token and it could not be refreshed. */
  expire(): void {
    if (this.session()) {
      this.clear();
      this.sessionExpired.set(true);
    }
  }

  private refreshOnce(): Observable<void> {
    // Another tab may have refreshed while we waited for the lock: adopt its session.
    const stored = localStore.getJson<Session>(STORAGE_KEY);
    const mine = this.session();
    if (stored && mine && stored.refreshToken !== mine.refreshToken && stored.expiresAt > Date.now()) {
      this.apply(stored);
      return of(undefined);
    }
    const refreshToken = mine?.refreshToken;
    if (!refreshToken || !this.canRefresh()) {
      return throwError(() => new Error('Session cannot be refreshed'));
    }
    return this.api.refresh(refreshToken).pipe(
      tap((response) => this.start(response)),
      map(() => undefined),
    );
  }

  /** Runs `task` while holding a lock shared by all tabs of this site (where the browser supports it). */
  private withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
    const locks = (navigator as Navigator & { locks?: LockManager }).locks;
    if (!locks) {
      return task();
    }
    return locks.request(REFRESH_LOCK, () => task()) as Promise<T>;
  }

  private start(response: AuthResponse): void {
    const expiresAt =
      readTokenExpiry(response.jwt) ??
      (response.accessTokenExpiresAt ? Date.parse(response.accessTokenExpiresAt) : Date.now() + 15 * 60 * 1000);
    const session: Session = {
      token: response.jwt,
      user: response.user,
      expiresAt,
      refreshToken: response.refreshToken ?? null,
      refreshExpiresAt: response.refreshTokenExpiresAt ? Date.parse(response.refreshTokenExpiresAt) : null,
    };
    localStore.setJson(STORAGE_KEY, session);
    this.sessionExpired.set(false);
    this.apply(session);
  }

  private restore(): void {
    const saved = localStore.getJson<Session>(STORAGE_KEY);
    const usable =
      !!saved &&
      typeof saved.token === 'string' &&
      !!saved.user &&
      (saved.expiresAt > Date.now() ||
        (!!saved.refreshToken && (saved.refreshExpiresAt ?? Infinity) > Date.now()));
    if (usable) {
      this.apply({ ...saved, refreshToken: saved.refreshToken ?? null, refreshExpiresAt: saved.refreshExpiresAt ?? null });
    } else {
      localStore.remove(STORAGE_KEY);
      this.apply(null);
    }
  }

  private clear(): void {
    localStore.remove(STORAGE_KEY);
    this.apply(null);
  }

  /** Schedules the next silent refresh (or the sign-out, when the session cannot be renewed). */
  private apply(session: Session | null): void {
    clearTimeout(this.expiryTimer);
    this.session.set(session);
    if (!session) {
      return;
    }
    if (this.canRefresh()) {
      const msLeft = Math.min(Math.max(session.expiresAt - Date.now() - REFRESH_AHEAD_MS, 0), MAX_TIMEOUT_MS);
      this.expiryTimer = setTimeout(() => this.refresh().subscribe({ error: () => this.expire() }), msLeft);
    } else {
      const msLeft = Math.min(session.expiresAt - Date.now(), MAX_TIMEOUT_MS);
      this.expiryTimer = setTimeout(() => this.expire(), Math.max(msLeft, 0));
    }
  }
}
