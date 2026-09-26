import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FileService } from './file.service';

describe('FileService', () => {
  let service: FileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the file and upload options as multipart data', () => {
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });

    service.upload(file, { expirationDays: 3, password: 'secret1' }).subscribe();

    const request = http.expectOne('/api/files');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBeTrue();
    const formData = request.request.body as FormData;
    const uploadedFile = formData.get('file') as File;
    expect(uploadedFile.name).toBe('document.pdf');
    expect(uploadedFile.size).toBe(file.size);
    expect(formData.get('expirationDays')).toBe('3');
    expect(formData.get('password')).toBe('secret1');
    request.flush({});
  });

  it('omits the optional password when it is empty', () => {
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });

    service.upload(file, { expirationDays: 7 }).subscribe();

    const request = http.expectOne('/api/files');
    expect((request.request.body as FormData).has('password')).toBeFalse();
    request.flush({});
  });

  it('loads public metadata without sending a password', () => {
    service.getSharedFile('share/token').subscribe();

    const request = http.expectOne('/api/shares/share%2Ftoken');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('requests a temporary download URL with the password', () => {
    service.authorizeDownload('share-token', 'download-secret').subscribe();

    const request = http.expectOne('/api/shares/share-token/download');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ password: 'download-secret' });
    request.flush({});
  });

  it('uses an empty object for an unprotected download', () => {
    service.authorizeDownload('share-token').subscribe();

    const request = http.expectOne('/api/shares/share-token/download');
    expect(request.request.body).toEqual({});
    request.flush({});
  });

  it('loads the authenticated users file history', () => {
    service.listOwnedFiles().subscribe();

    const request = http.expectOne('/api/files');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads one owned file by identifier', () => {
    service.getOwnedFile('file/id').subscribe();

    const request = http.expectOne('/api/files/file%2Fid');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
