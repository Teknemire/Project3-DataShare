import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiError, UserResponse } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private deletionTrigger: HTMLElement | null = null;

  protected readonly user = signal<UserResponse | null>(this.authService.currentUser());
  protected readonly isLoading = signal(true);
  protected readonly loadError = signal('');
  protected readonly menuOpen = signal(false);
  protected readonly deletionDialogOpen = signal(false);
  protected readonly isDeleting = signal(false);
  protected readonly deletionError = signal('');
  protected readonly accountDeleted = signal(false);

  protected readonly deletionForm = new FormGroup({
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(72)],
    }),
    understood: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue],
    }),
  });

  ngOnInit(): void {
    this.loadProfile();
  }

  protected loadProfile(): void {
    this.isLoading.set(true);
    this.loadError.set('');
    this.authService
      .loadCurrentUser()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (user) => this.user.set(user),
        error: (error: HttpErrorResponse) => this.handleLoadError(error),
      });
  }

  protected openDeletionDialog(event: Event): void {
    this.deletionTrigger = event.currentTarget as HTMLElement;
    this.deletionForm.reset({ password: '', understood: false });
    this.deletionError.set('');
    this.deletionDialogOpen.set(true);
    setTimeout(() => this.document.getElementById('account-password')?.focus());
  }

  protected cancelDeletion(): void {
    if (this.isDeleting()) {
      return;
    }
    this.deletionDialogOpen.set(false);
    this.deletionError.set('');
    const trigger = this.deletionTrigger;
    this.deletionTrigger = null;
    setTimeout(() => trigger?.focus());
  }

  protected confirmDeletion(): void {
    this.deletionError.set('');
    if (this.deletionForm.invalid || this.isDeleting()) {
      this.deletionForm.markAllAsTouched();
      return;
    }

    this.isDeleting.set(true);
    this.authService
      .deleteAccount({ password: this.deletionForm.controls.password.value })
      .pipe(finalize(() => this.isDeleting.set(false)))
      .subscribe({
        next: () => {
          this.deletionDialogOpen.set(false);
          this.deletionTrigger = null;
          this.user.set(null);
          this.accountDeleted.set(true);
          setTimeout(() => this.document.getElementById('account-deleted-title')?.focus());
        },
        error: (error: HttpErrorResponse) => this.handleDeletionError(error),
      });
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  private handleLoadError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      this.authService.logout();
      void this.router.navigate(['/login']);
      return;
    }
    this.loadError.set('Impossible de charger votre profil. Veuillez réessayer.');
  }

  private handleDeletionError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiError | undefined;
    if (error.status === 401 && apiError?.code === 'INVALID_CREDENTIALS') {
      this.deletionError.set('Le mot de passe saisi est incorrect.');
      return;
    }
    if (error.status === 401) {
      this.authService.logout();
      void this.router.navigate(['/login']);
      return;
    }
    if (error.status === 503) {
      this.deletionError.set(
        'Le stockage est indisponible. Votre compte et vos données n’ont pas été supprimés.',
      );
      return;
    }
    this.deletionError.set('Impossible de supprimer votre compte. Veuillez réessayer.');
  }
}
