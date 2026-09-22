import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { LoginResponse, UserResponse } from '../models/auth.models';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let tokenStorage: TokenStorageService;

  const user: UserResponse = {
    id: 'd52f8f24-6363-469c-91cb-5a4f3bd99369',
    email: 'user@example.com',
    createdAt: '2026-09-20T17:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    tokenStorage = TestBed.inject(TokenStorageService);
    tokenStorage.clear();
  });

  afterEach(() => {
    http.verify();
    tokenStorage.clear();
  });

  it('stores the JWT and current user returned by login', () => {
    const response: LoginResponse = {
      accessToken: 'signed-token',
      tokenType: 'Bearer',
      expiresAt: '2026-09-20T18:00:00Z',
      user,
    };

    service.login({ email: user.email, password: 'password123' }).subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush(response);

    expect(tokenStorage.get()).toBe('signed-token');
    expect(service.currentUser()).toEqual(user);
  });

  it('adds the bearer token when loading the current user', () => {
    tokenStorage.set('signed-token');

    service.loadCurrentUser().subscribe();
    const request = http.expectOne('/api/auth/me');

    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-token');
    request.flush(user);
    expect(service.currentUser()).toEqual(user);
  });

  it('clears authentication data on logout', () => {
    tokenStorage.set('signed-token');

    service.logout();

    expect(tokenStorage.get()).toBeNull();
    expect(service.currentUser()).toBeNull();
  });
});
