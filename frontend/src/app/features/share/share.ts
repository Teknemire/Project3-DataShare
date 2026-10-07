import { SiteHeader } from '../../shared/site-header/site-header';
import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiError } from '../../core/models/auth.models';
import { SharedFileResponse } from '../../core/models/file.models';
import { FileService } from '../../core/services/file.service';

type ShareState = 'loading' | 'ready' | 'expired' | 'unavailable';

@Component({
  selector: 'app-share',
  imports: [SiteHeader, ReactiveFormsModule],
  templateUrl: './share.html',
  styleUrl: './share.scss',
})
export class Share implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly fileService = inject(FileService);
  private readonly document = inject(DOCUMENT);
  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  protected readonly state = signal<ShareState>('loading');
  protected readonly sharedFile = signal<SharedFileResponse | null>(null);
  protected readonly feedback = signal('');
  protected readonly isSubmitting = signal(false);
  protected readonly password = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  ngOnInit(): void {
    if (!this.token) {
      this.state.set('unavailable');
      this.feedback.set('Ce lien n’est pas disponible.');
      return;
    }

    this.fileService.getSharedFile(this.token).subscribe({
      next: (file) => {
        this.sharedFile.set(file);
        this.state.set('ready');
      },
      error: (error: HttpErrorResponse) => this.handleShareError(error),
    });
  }

  protected download(): void {
    const file = this.sharedFile();
    if (!file || this.isSubmitting()) {
      return;
    }
    if (file.passwordProtected && this.password.invalid) {
      this.password.markAsTouched();
      return;
    }

    this.feedback.set('');
    this.isSubmitting.set(true);
    this.fileService
      .authorizeDownload(this.token, file.passwordProtected ? this.password.value : undefined)
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: ({ downloadUrl }) => this.startBrowserDownload(downloadUrl),
        error: (error: HttpErrorResponse) => this.handleDownloadError(error),
      });
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

  protected expirationMessage(expiresAt: string): string {
    const remainingDays = Math.max(
      1,
      Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 86_400_000),
    );
    return remainingDays === 1
      ? 'Ce fichier expirera demain.'
      : `Ce fichier expirera dans ${remainingDays} jours.`;
  }

  protected expiresSoon(expiresAt: string): boolean {
    return new Date(expiresAt).getTime() - Date.now() <= 86_400_000;
  }

  private startBrowserDownload(downloadUrl: string): void {
    if (!downloadUrl.startsWith('/api/shares/')) {
      this.feedback.set('Le téléchargement n’a pas pu être préparé.');
      return;
    }
    const link = this.document.createElement('a');
    link.href = downloadUrl;
    link.download = '';
    link.hidden = true;
    this.document.body.appendChild(link);
    link.click();
    link.remove();
  }

  private handleShareError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiError | undefined;
    if (error.status === 410) {
      this.state.set('expired');
      this.feedback.set(
        apiError?.message || 'Ce fichier n’est plus disponible au téléchargement car il a expiré.',
      );
    } else {
      this.state.set('unavailable');
      this.feedback.set(apiError?.message || 'Ce lien n’est pas disponible.');
    }
  }

  private handleDownloadError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiError | undefined;
    if (error.status === 401) {
      this.feedback.set('Authentification du téléchargement refusée.');
    } else if (error.status === 410) {
      this.state.set('expired');
      this.sharedFile.set(null);
      this.feedback.set(
        apiError?.message || 'Ce fichier n’est plus disponible au téléchargement car il a expiré.',
      );
    } else if (error.status === 404) {
      this.state.set('unavailable');
      this.sharedFile.set(null);
      this.feedback.set(apiError?.message || 'Ce lien n’est pas disponible.');
    } else if (error.status === 503) {
      this.feedback.set('Le stockage est temporairement indisponible. Veuillez réessayer.');
    } else {
      this.feedback.set('Le téléchargement n’a pas pu démarrer. Veuillez réessayer.');
    }
  }
}
