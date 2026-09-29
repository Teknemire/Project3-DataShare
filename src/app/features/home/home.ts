import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TokenStorageService } from '../../core/services/token-storage.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly tokenStorage = inject(TokenStorageService);

  protected readonly shareDestination = '/upload';

  protected get isAuthenticated(): boolean {
    return this.tokenStorage.get() !== null;
  }

  protected get accountDestination(): string {
    return this.isAuthenticated ? '/account' : '/login';
  }
}
