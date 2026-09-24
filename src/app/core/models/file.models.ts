export type FileStatus = 'ACTIVE' | 'EXPIRED';

export interface FileResponse {
  id: string;
  originalName: string;
  mimeType: string;
  size: number;
  createdAt: string;
  expiresAt: string;
  passwordProtected: boolean;
  shareUrl: string;
  status: FileStatus;
}

export interface UploadOptions {
  expirationDays: number;
  password?: string;
}

export interface SharedFileResponse {
  originalName: string;
  mimeType: string;
  size: number;
  expiresAt: string;
  passwordProtected: boolean;
}

export interface DownloadAccessResponse {
  downloadUrl: string;
  expiresAt: string;
}
