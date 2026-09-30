import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FileService } from '../../core/services/file.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { Upload } from './upload';

describe('Upload', () => {
  let fixture: ComponentFixture<Upload>;
  let fileService: jasmine.SpyObj<FileService>;
  let tokenStorage: TokenStorageService;

  beforeEach(async () => {
    fileService = jasmine.createSpyObj<FileService>('FileService', ['upload']);
    await TestBed.configureTestingModule({
      imports: [Upload],
      providers: [{ provide: FileService, useValue: fileService }, provideRouter([])],
    }).compileComponents();

    tokenStorage = TestBed.inject(TokenStorageService);
    tokenStorage.clear();
    fixture = TestBed.createComponent(Upload);
    fixture.detectChanges();
  });

  afterEach(() => tokenStorage.clear());

  it('offers login without blocking an anonymous upload', () => {
    expect(fixture.nativeElement.querySelector('.account-link')?.getAttribute('href')).toBe(
      '/login',
    );
    expect(fixture.nativeElement.querySelector('.account-link')?.textContent).toContain(
      'Se connecter',
    );
  });

  it('rejects a forbidden extension before calling the API', () => {
    selectFile(new File(['content'], 'program.exe', { type: 'application/octet-stream' }));

    expect(fileService.upload).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'type de fichier',
    );
    expect((fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).disabled)
      .toBeTrue();
  });

  it('uploads an allowed file with the selected expiration', () => {
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });
    fileService.upload.and.returnValue(
      of({
        id: 'file-id',
        originalName: 'document.pdf',
        mimeType: 'application/pdf',
        size: file.size,
        createdAt: '2026-09-22T12:00:00Z',
        expiresAt: '2026-09-29T12:00:00Z',
        passwordProtected: false,
        shareUrl: 'http://localhost:4200/share/token',
        status: 'ACTIVE',
        tags: [],
      }),
    );
    selectFile(file);

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fileService.upload).toHaveBeenCalledOnceWith(file, {
      expirationDays: 7,
      password: undefined,
      tags: undefined,
    });
    expect(fixture.nativeElement.querySelector('#share-link')?.value).toBe(
      'http://localhost:4200/share/token',
    );
  });

  it('allows an authenticated user to add tags to the upload', () => {
    fixture.destroy();
    tokenStorage.set('test-jwt');
    fixture = TestBed.createComponent(Upload);
    fixture.detectChanges();

    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });
    fileService.upload.and.returnValue(
      of({
        id: 'file-id',
        originalName: 'document.pdf',
        mimeType: 'application/pdf',
        size: file.size,
        createdAt: '2026-09-22T12:00:00Z',
        expiresAt: '2026-09-29T12:00:00Z',
        passwordProtected: false,
        shareUrl: 'http://localhost:4200/share/token',
        status: 'ACTIVE',
        tags: ['Projet', 'Urgent'],
      }),
    );
    selectFile(file);
    const tagsInput = fixture.nativeElement.querySelector('#tags') as HTMLInputElement;
    tagsInput.value = ' Projet, Urgent ';
    tagsInput.dispatchEvent(new Event('input'));

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();

    expect(fileService.upload).toHaveBeenCalledOnceWith(file, {
      expirationDays: 7,
      password: undefined,
      tags: ['Projet', 'Urgent'],
    });
  });

  it('rejects duplicate tags before calling the API', () => {
    fixture.destroy();
    tokenStorage.set('test-jwt');
    fixture = TestBed.createComponent(Upload);
    fixture.detectChanges();

    selectFile(new File(['content'], 'document.pdf', { type: 'application/pdf' }));
    const tagsInput = fixture.nativeElement.querySelector('#tags') as HTMLInputElement;
    tagsInput.value = 'Projet, projet';
    tagsInput.dispatchEvent(new Event('input'));
    tagsInput.dispatchEvent(new Event('blur'));
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();

    expect(fileService.upload).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#tags-error')?.textContent).toContain(
      'plusieurs fois',
    );
  });

  it('shows the server message for a file that is too large', () => {
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });
    fileService.upload.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 413 })),
    );
    selectFile(file);

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('1 Go');
  });

  function selectFile(file: File): void {
    const input = fixture.nativeElement.querySelector('#file') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: { item: () => file },
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }
});
