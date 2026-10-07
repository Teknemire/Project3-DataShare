import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [{ provide: AuthService, useValue: authService }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('does not send an invalid form and exposes its errors', () => {
    submitForm();

    expect(authService.login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#email-error')).not.toBeNull();
  });

  it('redirects to the protected account after a valid login', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    authService.login.and.returnValue(
      of({
        accessToken: 'signed-token',
        tokenType: 'Bearer',
        expiresAt: '2026-09-20T18:00:00Z',
        user: {
          id: 'd52f8f24-6363-469c-91cb-5a4f3bd99369',
          email: 'user@example.com',
          createdAt: '2026-09-20T17:00:00Z',
        },
      }),
    );
    setInputValue('#email', 'user@example.com');
    setInputValue('#password', 'password123');

    submitForm();

    expect(authService.login).toHaveBeenCalledOnceWith({
      email: 'user@example.com',
      password: 'password123',
    });
    expect(navigate).toHaveBeenCalledOnceWith(['/account']);
  });

  it('shows the same generic message for rejected credentials', () => {
    authService.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    setInputValue('#email', 'unknown@example.com');
    setInputValue('#password', 'wrong-password');

    submitForm();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Email ou mot de passe incorrect.',
    );
  });

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function setInputValue(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
});
