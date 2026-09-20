import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiError } from '../../../core/models/auth.models';
import { AuthService } from '../../../core/services/auth.service';

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  return control.get('password')?.value === control.get('passwordConfirmation')?.value
    ? null
    : { passwordsMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly authService = inject(AuthService);

  protected readonly isSubmitting = signal(false);
  protected readonly feedback = signal('');
  protected readonly registrationSucceeded = signal(false);

  protected readonly registrationForm = new FormGroup(
    {
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email, Validators.maxLength(320)],
      }),
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(72)],
      }),
      passwordConfirmation: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    this.feedback.set('');
    this.registrationSucceeded.set(false);

    if (this.registrationForm.invalid) {
      this.registrationForm.markAllAsTouched();
      this.feedback.set('Veuillez corriger les champs indiqués.');
      return;
    }

    const { email, password } = this.registrationForm.getRawValue();
    this.isSubmitting.set(true);

    this.authService
      .register({ email, password })
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.registrationSucceeded.set(true);
          this.feedback.set('Votre compte a bien été créé.');
          this.registrationForm.reset();
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private handleError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiError | undefined;

    if (error.status === 409 && apiError?.code === 'EMAIL_ALREADY_USED') {
      this.registrationForm.controls.email.setErrors({ emailAlreadyUsed: true });
      this.feedback.set('Un compte existe déjà avec cette adresse e-mail.');
      return;
    }

    this.feedback.set('Une erreur est survenue. Veuillez réessayer.');
  }
}
