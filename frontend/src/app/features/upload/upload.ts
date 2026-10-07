import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiError } from '../../core/models/auth.models';
import { FileResponse } from '../../core/models/file.models';
import { FileService } from '../../core/services/file.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { passwordByteLength } from '../../core/validators/password.validator';

const MAX_FILE_SIZE = 1_000_000_000;
const MAX_TAG_LENGTH = 30;
const ALLOWED_EXTENSIONS = new Set([
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'tif', 'tiff', 'heic', 'heif',
  'mp4', 'mov', 'avi', 'mkv', 'webm', 'mpeg', 'mpg', 'm4v',
  'mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac', 'wma',
  'zip', '7z', 'rar', 'tar', 'gz', 'gzip', 'bz2', 'xz',
  'pdf', 'txt', 'csv', 'rtf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'odt', 'ods', 'odp',
]);

function parseTags(value: string): string[] {
  return value
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
}

function validateTags(control: AbstractControl<string>): ValidationErrors | null {
  const tags = parseTags(control.value);
  if (tags.some((tag) => tag.length > MAX_TAG_LENGTH)) {
    return { tagTooLong: true };
  }
  const normalizedTags = tags.map((tag) => tag.toLocaleLowerCase());
  return new Set(normalizedTags).size === normalizedTags.length ? null : { duplicateTags: true };
}

@Component({
  selector: 'app-upload',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
})
export class Upload {
  private readonly fileService = inject(FileService);
  private readonly tokenStorage = inject(TokenStorageService);

  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly uploadResult = signal<FileResponse | null>(null);
  protected readonly feedback = signal('');
  protected readonly copyFeedback = signal('');
  protected readonly isSubmitting = signal(false);
  protected readonly isAuthenticated = this.tokenStorage.get() !== null;
  protected readonly accountDestination = this.isAuthenticated ? '/account' : '/login';
  protected readonly uploadForm = new FormGroup({
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.minLength(6), passwordByteLength],
    }),
    expirationDays: new FormControl(7, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(7)],
    }),
    tags: new FormControl('', {
      nonNullable: true,
      validators: [validateTags],
    }),
  });

  protected chooseFile(): void {
    this.fileInput?.nativeElement.click();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    this.feedback.set('');
    this.uploadResult.set(null);
    this.copyFeedback.set('');

    if (!file) {
      this.selectedFile.set(null);
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      this.selectedFile.set(null);
      this.feedback.set('Ce fichier dépasse la taille maximale autorisée de 1 Go.');
      input.value = '';
      return;
    }
    if (!ALLOWED_EXTENSIONS.has(this.extensionOf(file.name))) {
      this.selectedFile.set(null);
      this.feedback.set('Ce type de fichier n’est pas autorisé.');
      input.value = '';
      return;
    }

    this.selectedFile.set(file);
  }

  protected submit(): void {
    const file = this.selectedFile();
    this.feedback.set('');
    this.copyFeedback.set('');

    if (!file) {
      this.feedback.set('Choisissez un fichier à partager.');
      return;
    }
    if (this.uploadForm.invalid) {
      this.uploadForm.markAllAsTouched();
      this.feedback.set('Veuillez corriger les champs indiqués.');
      return;
    }

    this.isSubmitting.set(true);
    const values = this.uploadForm.getRawValue();
    this.fileService
      .upload(file, {
        expirationDays: values.expirationDays,
        password: values.password || undefined,
        tags: this.isAuthenticated ? parseTags(values.tags) : undefined,
      })
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: (result) => this.uploadResult.set(result),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  protected async copyShareLink(): Promise<void> {
    const shareUrl = this.uploadResult()?.shareUrl;
    if (!shareUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareUrl);
      this.copyFeedback.set('Lien copié dans le presse-papiers.');
    } catch {
      this.copyFeedback.set('Copie impossible. Sélectionnez le lien pour le copier.');
    }
  }

  protected formatSize(size: number): string {
    if (size < 1_000_000) {
      return `${Math.max(1, Math.round(size / 1_000))} Ko`;
    }
    return `${(size / 1_000_000).toFixed(size < 10_000_000 ? 1 : 0)} Mo`;
  }

  protected retentionLabel(): string {
    const days = this.uploadForm.controls.expirationDays.value;
    if (days === 7) {
      return 'une semaine';
    }
    if (days === 1) {
      return 'une journée';
    }
    return `${days} jours`;
  }

  private extensionOf(fileName: string): string {
    return fileName.includes('.') ? (fileName.split('.').pop()?.toLowerCase() ?? '') : '';
  }

  private handleError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiError | undefined;
    if (error.status === 413) {
      this.feedback.set('Ce fichier dépasse la taille maximale autorisée de 1 Go.');
    } else if (error.status === 415) {
      this.feedback.set('Ce type de fichier n’est pas autorisé.');
    } else if (error.status === 503) {
      this.feedback.set('Le stockage est temporairement indisponible. Veuillez réessayer.');
    } else {
      this.feedback.set(apiError?.message || 'L’envoi a échoué. Veuillez réessayer.');
    }
  }
}
