import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FileService } from '../../core/services/file.service';
import { Share } from './share';

describe('Share', () => {
  let fixture: ComponentFixture<Share>;
  let fileService: jasmine.SpyObj<FileService>;

  beforeEach(async () => {
    fileService = jasmine.createSpyObj<FileService>('FileService', [
      'getSharedFile',
      'authorizeDownload',
    ]);
    fileService.getSharedFile.and.returnValue(
      of({
        originalName: 'document.pdf',
        mimeType: 'application/pdf',
        size: 2_000_000,
        expiresAt: '2099-09-29T12:00:00Z',
        passwordProtected: true,
      }),
    );

    await TestBed.configureTestingModule({
      imports: [Share],
      providers: [
        provideRouter([]),
        { provide: FileService, useValue: fileService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ token: 'share-token' }) } },
        },
      ],
    }).compileComponents();
  });

  it('shows the public file information and requires the password', () => {
    createComponent();

    expect(fileService.getSharedFile).toHaveBeenCalledOnceWith('share-token');
    expect(fixture.nativeElement.querySelector('.file-details strong')?.textContent).toContain(
      'document.pdf',
    );
    expect((fixture.nativeElement.querySelector('.download-button') as HTMLButtonElement).disabled)
      .toBeTrue();
  });

  it('authorizes the password before starting a native browser download', () => {
    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
    fileService.authorizeDownload.and.returnValue(
      of({
        downloadUrl: '/api/shares/share-token/content?ticket=signed-ticket',
        expiresAt: '2099-09-29T12:01:00Z',
      }),
    );
    createComponent();
    enterPassword('download-secret');

    (fixture.nativeElement.querySelector('.download-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fileService.authorizeDownload).toHaveBeenCalledOnceWith(
      'share-token',
      'download-secret',
    );
    expect(clickSpy).toHaveBeenCalled();
  });

  it('uses the same message when download authentication is refused', () => {
    fileService.authorizeDownload.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              code: 'DOWNLOAD_AUTH_FAILED',
              message: 'Authentification du téléchargement refusée.',
            },
          }),
      ),
    );
    createComponent();
    enterPassword('wrong-password');

    (fixture.nativeElement.querySelector('.download-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Authentification du téléchargement refusée.',
    );
  });

  it('shows the expired state without a download button', () => {
    fileService.getSharedFile.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 410,
            error: {
              code: 'SHARE_EXPIRED',
              message: 'Ce fichier n’est plus disponible au téléchargement car il a expiré.',
            },
          }),
      ),
    );
    createComponent();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'a expiré',
    );
    expect(fixture.nativeElement.querySelector('.download-button')).toBeNull();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(Share);
    fixture.detectChanges();
  }

  function enterPassword(value: string): void {
    const input = fixture.nativeElement.querySelector('#download-password') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
});
