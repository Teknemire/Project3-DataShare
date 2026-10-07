import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TokenStorageService } from '../../core/services/token-storage.service';

@Component({
  selector: 'app-site-header',
  imports: [RouterLink],
  template: `
    <header class="site-header">
      <a class="brand" routerLink="/" aria-label="Accueil DataShare">DataShare</a>
      <a class="account-link" [routerLink]="session.isAuthenticated() ? '/account' : '/login'">
        {{ session.isAuthenticated() ? 'Mon espace' : 'Se connecter' }}
      </a>
    </header>
  `,
  styleUrl: './site-header.scss',
})
export class SiteHeader {
  protected readonly session = inject(TokenStorageService);
}
