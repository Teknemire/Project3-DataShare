import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserResponse } from '../../core/models/auth.models';
import { FileResponse } from '../../core/models/file.models';
import { AuthService } from '../../core/services/auth.service';
import { FileService } from '../../core/services/file.service';
import { Account } from './account';

describe('Account', () => {
  let fixture: ComponentFixture<Account>;
  let authService: jasmine.SpyObj<AuthService>;
  let fileService: jasmine.SpyObj<FileService>;

  const user: UserResponse = {
    id: 'user-id',
    email: 'owner@example.com',
    createdAt: '2026-09-20T12:00:00Z',
  };

  const activeFile: FileResponse = {
    id: 'active-id',
    originalName: 'active.pdf',
    mimeType: 'application/pdf',
    size: 2_000_000,
    createdAt: '2026-09-22T12:00:00Z',
    expiresAt: '2099-09-29T12:00:00Z',
    passwordProtected: true,
    shareUrl: 'http://localhost:4200/share/active-token',
    status: 'ACTIVE',
  };

  const expiredFile: FileResponse = {
    ...activeFile,
    id: 'expired-id',
    originalName: 'expired.pdf',
    expiresAt: '2020-09-20T12:00:00Z',
    passwordProtected: false,
    shareUrl: 'http://localhost:4200/share/expired-token',
    status: 'EXPIRED',
  };

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['loadCurrentUser', 'logout'],
      { currentUser: signal<UserResponse | null>(null) },
    );
    fileService = jasmine.createSpyObj<FileService>('FileService', ['listOwnedFiles']);
    authService.loadCurrentUser.and.returnValue(of(user));
    fileService.listOwnedFiles.and.returnValue(of([activeFile, expiredFile]));

    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: FileService, useValue: fileService },
      ],
    }).compileComponents();
  });

  it('loads and displays active and expired files for the current user', () => {
    createComponent();

    expect(authService.loadCurrentUser).toHaveBeenCalled();
    expect(fileService.listOwnedFiles).toHaveBeenCalled();
    const rows = fixture.nativeElement.querySelectorAll('.file-row');
    expect(rows.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('active.pdf');
    expect(fixture.nativeElement.textContent).toContain('expired.pdf');
    expect(fixture.nativeElement.querySelector('.lock-icon')).not.toBeNull();
  });

  it('filters the history without another API request', () => {
    createComponent();
    const expiredFilter = [...fixture.nativeElement.querySelectorAll('.filters button')].find(
      (button: Element) => button.textContent?.trim() === 'Expirés',
    ) as HTMLButtonElement;

    expiredFilter.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.file-row').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('expired.pdf');
    expect(fixture.nativeElement.textContent).not.toContain('active.pdf');
    expect(fileService.listOwnedFiles).toHaveBeenCalledTimes(1);
  });

  it('only offers access for an active file', () => {
    createComponent();

    const accessLinks = fixture.nativeElement.querySelectorAll('.access-file');
    expect(accessLinks.length).toBe(1);
    expect(accessLinks[0].getAttribute('href')).toBe(
      'http://localhost:4200/share/active-token',
    );
    expect(fixture.nativeElement.querySelector('.expired-note')?.textContent).toContain(
      'plus stocké',
    );
  });

  it('shows an empty state when the user has no files', () => {
    fileService.listOwnedFiles.and.returnValue(of([]));
    createComponent();

    expect(fixture.nativeElement.querySelector('.empty-state')?.textContent).toContain(
      'aucun fichier',
    );
    expect(fixture.nativeElement.querySelector('.empty-state a')?.getAttribute('href')).toBe(
      '/upload',
    );
  });

  it('opens and closes the mobile navigation with the menu button', () => {
    createComponent();
    const menuButton = fixture.nativeElement.querySelector('.menu-button') as HTMLButtonElement;

    expect(menuButton.getAttribute('aria-expanded')).toBe('false');

    menuButton.click();
    fixture.detectChanges();

    expect(menuButton.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.sidebar').classList).toContain('sidebar-open');
    const backdrop = fixture.nativeElement.querySelector('.menu-backdrop') as HTMLButtonElement;
    expect(backdrop).not.toBeNull();

    backdrop.click();
    fixture.detectChanges();

    expect(menuButton.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('.menu-backdrop')).toBeNull();
  });

  it('logs out and redirects when the API rejects the JWT', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fileService.listOwnedFiles.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );

    createComponent();

    expect(authService.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledOnceWith(['/login']);
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(Account);
    fixture.detectChanges();
  }
});
