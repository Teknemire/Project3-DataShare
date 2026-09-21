import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { UserResponse } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-account',
  imports: [RouterLink],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = signal<UserResponse | null>(this.authService.currentUser());
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');

  ngOnInit(): void {
    this.authService
      .loadCurrentUser()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (user) => this.user.set(user),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  protected logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  private handleError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      this.authService.logout();
      void this.router.navigate(['/login']);
      return;
    }

    this.errorMessage.set('Impossible de charger votre compte. Veuillez réessayer.');
  }
}
