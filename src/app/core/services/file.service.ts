import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DownloadAccessResponse,
  FileResponse,
  SharedFileResponse,
  UploadOptions,
} from '../models/file.models';

@Injectable({ providedIn: 'root' })
export class FileService {
  private readonly http = inject(HttpClient);

  upload(file: File, options: UploadOptions): Observable<FileResponse> {
    const body = new FormData();
    body.append('file', file, file.name);
    body.append('expirationDays', options.expirationDays.toString());
    if (options.password) {
      body.append('password', options.password);
    }

    return this.http.post<FileResponse>('/api/files', body);
  }

  getSharedFile(token: string): Observable<SharedFileResponse> {
    return this.http.get<SharedFileResponse>(`/api/shares/${encodeURIComponent(token)}`);
  }

  authorizeDownload(token: string, password?: string): Observable<DownloadAccessResponse> {
    return this.http.post<DownloadAccessResponse>(
      `/api/shares/${encodeURIComponent(token)}/download`,
      password ? { password } : {},
    );
  }

  listOwnedFiles(): Observable<FileResponse[]> {
    return this.http.get<FileResponse[]>('/api/files');
  }

  getOwnedFile(id: string): Observable<FileResponse> {
    return this.http.get<FileResponse>(`/api/files/${encodeURIComponent(id)}`);
  }

  deleteOwnedFile(id: string): Observable<void> {
    return this.http.delete<void>(`/api/files/${encodeURIComponent(id)}`);
  }
}
