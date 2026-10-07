import { computed, Injectable, signal } from '@angular/core';
import { UserResponse } from '../models/auth.models';

const TOKEN_KEY = 'datashare_access_token';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly token = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));
  readonly isAuthenticated = computed(() => this.token() !== null);
  readonly currentUser = signal<UserResponse | null>(null);

  get(): string | null {
    return this.token();
  }

  set(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
  }

  clear(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
    this.currentUser.set(null);
  }
}
