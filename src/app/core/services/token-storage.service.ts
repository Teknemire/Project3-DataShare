import { Injectable } from '@angular/core';

const TOKEN_KEY = 'datashare_access_token';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  get(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  set(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
  }

  clear(): void {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}
