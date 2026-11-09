import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuthResponse, LoginRequest, SignupRequest, User } from '../models/api-models';

/**
 * HTTP calls for signing in and the signed-in user's profile.
 * "Api" classes only talk to the server. They hold no state; AuthStore does that.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/login`, request);
  }

  signup(request: SignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/signup`, request);
  }

  /** Spends `refreshToken` and returns a new access token and the next refresh token. */
  refresh(refreshToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/refresh`, { refreshToken });
  }

  /** Revokes the session on the server (refresh token family and the current access token). */
  logout(refreshToken: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/logout`, { refreshToken });
  }

  profile(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/api/users/profile`);
  }
}
