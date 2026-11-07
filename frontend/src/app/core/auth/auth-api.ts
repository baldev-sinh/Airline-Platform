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

  profile(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/api/users/profile`);
  }
}
