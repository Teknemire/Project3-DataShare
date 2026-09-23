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
});
