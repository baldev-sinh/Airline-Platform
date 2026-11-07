import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { AuthResponse, LoginRequest, SignupRequest, User } from '../models/api-models';
import { localStore } from '../util/browser-storage';
import { AuthApi } from './auth-api';
import { readTokenExpiry } from './jwt';

/** What we remember about a signed-in user. */
interface Session {
  readonly token: string;
  readonly user: User;
  /** Milliseconds since 1970 (same unit as Date.now()). */
  readonly expiresAt: number;
}

const STORAGE_KEY = 'baldev.session';

/** Browsers can't schedule timers further than ~24.8 days ahead (2^31 - 1 ms). */
const MAX_TIMEOUT_MS = 2_147_483_647;

/**
 * The single source of truth for "who is signed in".
 *
 * State lives in a signal: components read `auth.user()` or `auth.isSignedIn()` and update
 * automatically when it changes. No manual subscriptions needed.
 *
 * Where is the token kept? The backend returns the JWT in the response body and expects it in the
 * `Authorization` header, so JavaScript must be able to read it. We keep it in localStorage so a
 * refresh doesn't sign the user out, and limit the risk by:
 *  - signing out automatically when the token expires (it lives 1 hour),
 *  - sending it only to our own API (see auth-interceptor.ts),
 *  - a strict Content-Security-Policy that blocks injected scripts (see nginx/default.conf).
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);

  private readonly session = signal<Session | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

  /** The signed-in user, or null. */
  readonly user = computed(() => this.session()?.user ?? null);
  readonly isSignedIn = computed(() => this.session() !== null);
  /** True after the session ran out (not after a normal sign-out), so the UI can explain why. */
  readonly sessionExpired = signal(false);

  constructor() {
    this.restore();
    // If the user signs in or out in another tab, follow along in this one.
    window.addEventListener('storage', (event) => {
      if (event.key === STORAGE_KEY) {
        this.restore();
      }
    });
  }

  /** The raw token for the Authorization header, or null if signed out or expired. */
  token(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (current.expiresAt <= Date.now()) {
      this.expire();
      return null;
    }
    return current.token;
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

  /** Normal sign-out from the menu. */
  logout(): void {
    this.clear();
    this.sessionExpired.set(false);
  }

  /** The server rejected our token, or it ran out. */
  expire(): void {
    if (this.session()) {
      this.clear();
      this.sessionExpired.set(true);
    }
  }

  private start(response: AuthResponse): void {
    // Fallback of 1 hour matches the backend default (JWT_EXPIRATION=PT1H) if `exp` is missing.
    const expiresAt = readTokenExpiry(response.jwt) ?? Date.now() + 60 * 60 * 1000;
    const session: Session = { token: response.jwt, user: response.user, expiresAt };
    localStore.setJson(STORAGE_KEY, session);
    this.sessionExpired.set(false);
    this.apply(session);
  }

  private restore(): void {
    const saved = localStore.getJson<Session>(STORAGE_KEY);
    const isValid =
      !!saved && typeof saved.token === 'string' && !!saved.user && saved.expiresAt > Date.now();
    if (isValid) {
      this.apply(saved);
    } else {
      localStore.remove(STORAGE_KEY);
      this.apply(null);
    }
  }

  private clear(): void {
    localStore.remove(STORAGE_KEY);
    this.apply(null);
  }

  private apply(session: Session | null): void {
    clearTimeout(this.expiryTimer);
    this.session.set(session);
    if (session) {
      const msLeft = Math.min(session.expiresAt - Date.now(), MAX_TIMEOUT_MS);
      this.expiryTimer = setTimeout(() => this.expire(), msLeft);
    }
  }
}
