import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { UserResponse } from '../../core/models/auth.models';
import { FileResponse, FileStatus } from '../../core/models/file.models';
import { AuthService } from '../../core/services/auth.service';
import { FileService } from '../../core/services/file.service';

type HistoryFilter = 'ALL' | FileStatus;

@Component({
  selector: 'app-account',
  imports: [RouterLink],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly fileService = inject(FileService);
  private readonly router = inject(Router);

  protected readonly user = signal<UserResponse | null>(this.authService.currentUser());
  protected readonly files = signal<FileResponse[]>([]);
  protected readonly selectedFilter = signal<HistoryFilter>('ALL');
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly menuOpen = signal(false);
  protected readonly filteredFiles = computed(() => {
    const filter = this.selectedFilter();
    return filter === 'ALL' ? this.files() : this.files().filter((file) => file.status === filter);
  });

  ngOnInit(): void {
    this.loadAccount();
  }

  protected loadAccount(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    forkJoin({
      user: this.authService.loadCurrentUser(),
      files: this.fileService.listOwnedFiles(),
    })
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: ({ user, files }) => {
          this.user.set(user);
          this.files.set(files);
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  protected selectFilter(filter: HistoryFilter): void {
    this.selectedFilter.set(filter);
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

  protected formatSize(size: number): string {
    if (size < 1_000_000) {
      return `${Math.max(1, Math.round(size / 1_000))} Ko`;
    }
    if (size < 1_000_000_000) {
      return `${(size / 1_000_000).toFixed(size < 10_000_000 ? 1 : 0)} Mo`;
    }
    return `${(size / 1_000_000_000).toFixed(1)} Go`;
  }

  protected sentDate(createdAt: string): string {
    return new Intl.DateTimeFormat('fr-FR').format(new Date(createdAt));
  }

  protected expirationLabel(file: FileResponse): string {
    if (file.status === 'EXPIRED') {
      return 'Expiré';
    }
    const days = Math.max(
      1,
      Math.ceil((new Date(file.expiresAt).getTime() - Date.now()) / 86_400_000),
    );
    return days === 1 ? 'Expire demain' : `Expire dans ${days} jours`;
  }

  private handleError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      this.authService.logout();
      void this.router.navigate(['/login']);
      return;
    }

    this.errorMessage.set('Impossible de charger vos fichiers. Veuillez réessayer.');
  }
}
