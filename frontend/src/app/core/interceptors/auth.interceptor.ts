import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStorageService } from '../services/token-storage.service';

const PUBLIC_AUTH_ENDPOINTS = ['/api/auth/register', '/api/auth/login'];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(TokenStorageService).get();
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
  );
};
