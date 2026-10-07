import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from '../services/token-storage.service';

const PUBLIC_AUTH_ENDPOINTS = ['/api/auth/register', '/api/auth/login'];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(TokenStorageService);
  const router = inject(Router);
  const token = session.get();
  const isProtectedApiRequest =
    request.url.startsWith('/api/') &&
    !PUBLIC_AUTH_ENDPOINTS.includes(request.url) &&
    !request.url.startsWith('/api/shares/');

  if (!token || !isProtectedApiRequest) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  ).pipe(catchError((error: HttpErrorResponse) => {
    // Les 401 métier (mot de passe du partage ou du compte) conservent la session.
    if (error.status === 401 &&
        ['AUTHENTICATION_REQUIRED', 'INVALID_TOKEN'].includes(error.error?.code) &&
        session.get() === token) {
      session.clear();
      void router.navigate(['/login']);
    }
    return throwError(() => error);
  }));
};
