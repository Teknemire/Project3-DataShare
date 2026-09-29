import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  DeleteAccountRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserResponse,
} from '../models/auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);

  readonly currentUser = signal<UserResponse | null>(null);

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>('/api/auth/register', request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
      tap((response) => {
        this.tokenStorage.set(response.accessToken);
        this.currentUser.set(response.user);
      }),
    );
  }

  loadCurrentUser(): Observable<UserResponse> {
    return this.http
      .get<UserResponse>('/api/auth/me')
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  deleteAccount(request: DeleteAccountRequest): Observable<void> {
    return this.http
      .delete<void>('/api/users/me', { body: request })
      .pipe(tap(() => this.logout()));
  }

  logout(): void {
    this.tokenStorage.clear();
    this.currentUser.set(null);
  }
}
