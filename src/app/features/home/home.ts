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

  protected readonly isAuthenticated = this.tokenStorage.get() !== null;
  protected readonly accountDestination = this.isAuthenticated ? '/account' : '/login';
  protected readonly shareDestination = this.isAuthenticated ? '/upload' : '/login';
}
