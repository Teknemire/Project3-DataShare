import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { FileService } from '../services/file.service';
import { TokenStorageService } from '../services/token-storage.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let session: TokenStorageService;
  let auth: AuthService;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(TokenStorageService);
    auth = TestBed.inject(AuthService);
    session.set('original-token');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  afterEach(() => { http.verify(); session.clear(); });

  it('clears the shared user and redirects only once for concurrent session failures', () => {
    auth.currentUser.set({ id: 'id', email: 'test@example.com', createdAt: '' });
    auth.loadCurrentUser().subscribe({ error: () => undefined });
    auth.loadCurrentUser().subscribe({ error: () => undefined });
    const requests = http.match('/api/auth/me');
    for (const request of requests) {
      request.flush({ code: 'INVALID_TOKEN' }, { status: 401, statusText: 'Unauthorized' });
    }
    expect(session.isAuthenticated()).toBeFalse();
    expect(auth.currentUser()).toBeNull();
    expect(navigate).toHaveBeenCalledOnceWith(['/login']);
  });

  it('preserves the session when the account password is wrong', () => {
    auth.deleteAccount({ password: 'wrong' }).subscribe({ error: () => undefined });
    http.expectOne('/api/users/me').flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });
    expect(session.isAuthenticated()).toBeTrue();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('leaves public share password failures to the download screen', () => {
    TestBed.inject(FileService).authorizeDownload('token', 'wrong').subscribe({ error: () => undefined });
    const request = http.expectOne('/api/shares/token/download');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({ code: 'DOWNLOAD_AUTH_FAILED' }, { status: 401, statusText: 'Unauthorized' });
    expect(session.isAuthenticated()).toBeTrue();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('preserves a newer session when an old in-flight request fails', () => {
    auth.loadCurrentUser().subscribe({ error: () => undefined });
    const request = http.expectOne('/api/auth/me');
    session.set('new-token');
    request.flush({ code: 'INVALID_TOKEN' }, { status: 401, statusText: 'Unauthorized' });
    expect(session.get()).toBe('new-token');
    expect(navigate).not.toHaveBeenCalled();
  });
});
