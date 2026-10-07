import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserResponse } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { Profile } from './profile';

describe('Profile', () => {
  let fixture: ComponentFixture<Profile>;
  let authService: jasmine.SpyObj<AuthService>;

  const user: UserResponse = {
    id: 'user-id',
    email: 'owner@example.com',
    createdAt: '2026-09-20T12:00:00Z',
  };

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['loadCurrentUser', 'deleteAccount', 'logout'],
      { currentUser: signal<UserResponse | null>(null) },
    );
    authService.loadCurrentUser.and.returnValue(of(user));
    authService.deleteAccount.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();
  });

  it('loads the profile and displays the minimum account information', () => {
    createComponent();

    expect(authService.loadCurrentUser).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.profile-card')?.textContent).toContain(
      user.email,
    );
    expect(fixture.nativeElement.querySelector('.danger-zone')?.textContent).toContain(
      'Supprimer mon compte',
    );
  });

  it('requires the password and explicit acknowledgement before deletion', () => {
    createComponent();
    openDeletionDialog();

    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    const confirmButton = dialog.querySelector(
      '.confirm-account-deletion',
    ) as HTMLButtonElement;
    expect(dialog.closest('[inert]')).toBeNull();
    expect(confirmButton.disabled).toBeTrue();

    setInputValue(dialog.querySelector('#account-password') as HTMLInputElement, 'current-password');
    const checkbox = dialog.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.click();
    fixture.detectChanges();

    expect(confirmButton.disabled).toBeFalse();
    confirmButton.click();
    fixture.detectChanges();

    expect(authService.deleteAccount).toHaveBeenCalledOnceWith({
      password: 'current-password',
    });
    expect(fixture.nativeElement.querySelector('.deleted-page')?.textContent).toContain(
      'Votre compte a été supprimé',
    );
  });

  it('cancels without deleting the account', () => {
    createComponent();
    openDeletionDialog();

    const cancelButton = fixture.nativeElement.querySelector(
      '.dialog-actions button[type="button"]',
    ) as HTMLButtonElement;
    cancelButton.click();
    fixture.detectChanges();

    expect(authService.deleteAccount).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('keeps the dialog open when the password is incorrect', () => {
    authService.deleteAccount.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' },
          }),
      ),
    );
    createComponent();
    submitValidDeletion();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.dialog-error')?.textContent).toContain(
      'mot de passe saisi est incorrect',
    );
    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('explains that data is retained when storage deletion fails', () => {
    authService.deleteAccount.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    createComponent();
    submitValidDeletion();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.dialog-error')?.textContent).toContain(
      'compte et vos données n’ont pas été supprimés',
    );
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
  }

  function openDeletionDialog(): void {
    const deleteButton = fixture.nativeElement.querySelector(
      '.delete-account',
    ) as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();
  }

  function submitValidDeletion(): void {
    openDeletionDialog();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    setInputValue(dialog.querySelector('#account-password') as HTMLInputElement, 'current-password');
    (dialog.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
    fixture.detectChanges();
    (dialog.querySelector('.confirm-account-deletion') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function setInputValue(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
});
