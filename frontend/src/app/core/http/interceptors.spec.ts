import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { AuthStore } from '../auth/auth-store';
import { ApiError } from './api-error';
import { authInterceptor, errorInterceptor, isApiRequest } from './interceptors';

describe('isApiRequest', () => {
  it('treats relative paths as our API when the base URL is empty', () => {
    expect(isApiRequest('/api/bookings', '')).toBe(true);
    expect(isApiRequest('//cdn.example/x.js', '')).toBe(false);
    expect(isApiRequest('https://rzp.io/x', '')).toBe(false);
  });

  it('matches only URLs under a configured base URL', () => {
    expect(isApiRequest('https://api.example/api/x', 'https://api.example')).toBe(true);
    expect(isApiRequest('https://api.example.evil/api/x', 'https://api.example')).toBe(false);
  });
});

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | null;
  let refreshable: boolean;
  const expire = vi.fn();
  const refresh = vi.fn();

  beforeEach(() => {
    token = 'test-token';
    refreshable = false;
    expire.mockReset();
    refresh.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        // A fake AuthStore: the test controls the token and watches expire().
        {
          provide: AuthStore,
          useValue: { token: () => token, expire, refresh, canRefresh: () => refreshable },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify()); // fails the test if a request was left unanswered

  it('adds the bearer token to API requests', () => {
    http.get('/api/bookings/user/history').subscribe();
    const request = backend.expectOne('/api/bookings/user/history');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush([]);
  });

  it('never sends the token to other sites', () => {
    http.get('https://cdn.example/logo.png').subscribe();
    const request = backend.expectOne('https://cdn.example/logo.png');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush('');
  });

  it('sends no header when signed out', () => {
    token = null;
    http.get('/api/airports').subscribe();
    const request = backend.expectOne('/api/airports');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });

  it('signs out and opens sign-in when the server rejects the token', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let received: unknown;
    http.get('/api/bookings/1').subscribe({ error: (e: unknown) => (received = e) });

    backend
      .expectOne('/api/bookings/1')
      .flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    expect(received).toBeInstanceOf(ApiError);
    expect(expire).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(
      ['/login'],
      expect.objectContaining({ queryParams: expect.objectContaining({ reason: 'expired' }) }),
    );
  });

  it('renews the session once and repeats the request after a 401', () => {
    refreshable = true;
    refresh.mockImplementation(() => {
      token = 'new-token';
      return of(undefined);
    });
    let received: unknown;
    http.get('/api/bookings/1').subscribe((body) => (received = body));

    backend.expectOne('/api/bookings/1').flush({}, { status: 401, statusText: 'Unauthorized' });
    const retried = backend.expectOne('/api/bookings/1');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush({ id: 1 });

    expect(received).toEqual({ id: 1 });
    expect(refresh).toHaveBeenCalledOnce();
    expect(expire).not.toHaveBeenCalled();
  });

  it('signs out when the session cannot be renewed', () => {
    refreshable = true;
    refresh.mockReturnValue(throwError(() => new Error('revoked')));
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.get('/api/bookings/1').subscribe({ error: () => undefined });

    backend.expectOne('/api/bookings/1').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(expire).toHaveBeenCalledOnce();
  });

  it('leaves a wrong-password 401 to the login form', () => {
    token = null; // not signed in yet, so no Authorization header
    http.post('/auth/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(expire).not.toHaveBeenCalled();
  });
});
