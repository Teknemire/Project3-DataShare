import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['register']);
    authService.register.and.returnValue(
      of({
        id: 'd52f8f24-6363-469c-91cb-5a4f3bd99369',
        email: 'user@example.com',
        createdAt: '2026-09-20T17:00:00Z',
      }),
    );

    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [{ provide: AuthService, useValue: authService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
  });

  it('does not send an invalid form and displays accessible errors', () => {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;

    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(authService.register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#email-error')).not.toBeNull();
  });

  it('registers a user when the form is valid', () => {
    setInputValue('#email', 'user@example.com');
    setInputValue('#password', 'password123');
    setInputValue('#password-confirmation', 'password123');

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(authService.register).toHaveBeenCalledOnceWith({
      email: 'user@example.com',
      password: 'password123',
    });
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Votre compte a bien été créé.',
    );
  });

  function setInputValue(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
});
