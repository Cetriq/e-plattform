import { api, handleUnauthorized } from './client';
import { upload } from '@vercel/blob/client';
import { API_BASE_URL, FILE_STORAGE } from '@/lib/config';
import { getStoredToken } from '@/lib/auth';

/**
 * File upload/download API.
 */

export interface Attachment {
  id: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  fileSizeFormatted: string;
  caseId?: string;
  queryDefinitionId?: string;
  uploadedBy: string;
  uploadedAt: string;
  isImage: boolean;
  isPdf: boolean;
  /** Backend path, or a path under /api/blob/ for files in Vercel Blob. */
  downloadUrl: string;
  blobPathname?: string | null;
}

export interface UploadProgress {
  loaded: number;
  total: number;
  percentage: number;
}

/**
 * Upload a file to a case. Depending on the environment the file goes through
 * the backend, or straight from the browser to Vercel Blob.
 */
export async function uploadFile(
  file: File,
  options?: {
    caseId?: string;
    queryDefinitionId?: string;
    onProgress?: (progress: UploadProgress) => void;
  }
): Promise<Attachment> {
  if (FILE_STORAGE === 'blob') {
    return uploadToBlob(file, options);
  }
  return uploadThroughBackend(file, options);
}

/** Keep the extension but avoid characters that are awkward in storage paths. */
function storageName(filename: string): string {
  return filename
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '') // å -> a, ö -> o
    .replace(/[^\w.-]+/g, '-').replace(/-+/g, '-').slice(-100) || 'fil';
}

async function uploadToBlob(
  file: File,
  options?: {
    caseId?: string;
    queryDefinitionId?: string;
    onProgress?: (progress: UploadProgress) => void;
  }
): Promise<Attachment> {
  if (!options?.caseId) {
    throw new Error('Ärendet måste sparas innan filer kan laddas upp');
  }
  const token = getStoredToken();
  const authHeaders: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

  const blob = await upload(`cases/${options.caseId}/${storageName(file.name)}`, file, {
    access: 'private',
    handleUploadUrl: '/api/blob/upload',
    clientPayload: JSON.stringify({ caseId: options.caseId }),
    headers: authHeaders,
    multipart: file.size > 5 * 1024 * 1024,
    onUploadProgress: (progress) => options.onProgress?.(progress),
  });

  const response = await fetch('/api/blob/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders },
    body: JSON.stringify({
      pathname: blob.pathname,
      originalFilename: file.name,
      caseId: options.caseId,
      queryDefinitionId: options.queryDefinitionId,
    }),
  });
  if (!response.ok) {
    handleUnauthorized(response.status);
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error || 'Uppladdning misslyckades');
  }
  return response.json();
}

async function uploadThroughBackend(
  file: File,
  options?: {
    caseId?: string;
    queryDefinitionId?: string;
    onProgress?: (progress: UploadProgress) => void;
  }
): Promise<Attachment> {
  const formData = new FormData();
  formData.append('file', file);

  if (options?.caseId) {
    formData.append('caseId', options.caseId);
  }
  if (options?.queryDefinitionId) {
    formData.append('queryDefinitionId', options.queryDefinitionId);
  }

  // Use XMLHttpRequest for progress tracking
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && options?.onProgress) {
        options.onProgress({
          loaded: event.loaded,
          total: event.total,
          percentage: Math.round((event.loaded / event.total) * 100),
        });
      }
    });

    xhr.addEventListener('load', () => {
      handleUnauthorized(xhr.status);
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText));
      } else {
        let message = xhr.statusText || 'Uppladdning misslyckades';
        try {
          const body = JSON.parse(xhr.responseText);
          message = body.message || body.error || message;
        } catch {
          // Ignore non-JSON error bodies
        }
        reject(new Error(message));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Upload failed'));
    });

    xhr.open('POST', `${API_BASE_URL}/api/v1/files`);
    const token = getStoredToken();
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    xhr.send(formData);
  });
}

/**
 * Get attachment metadata.
 */
export async function getAttachment(attachmentId: string): Promise<Attachment> {
  return api.get<Attachment>(`/api/v1/files/${attachmentId}`);
}

/**
 * Get download URL for an attachment.
 */
export function getDownloadUrl(attachmentId: string): string {
  return `${API_BASE_URL}/api/v1/files/${attachmentId}/download`;
}

/**
 * Download an attachment with the user's token and save it.
 */
export async function downloadAttachment(attachment: Attachment): Promise<void> {
  return api.downloadBlob(attachment.downloadUrl, attachment.originalFilename);
}

/**
 * Fetch an attachment with the user's token, e.g. to show an image preview.
 */
export async function getAttachmentBlob(attachment: Attachment): Promise<Blob> {
  return api.getBlob(attachment.downloadUrl);
}

/**
 * Get pre-signed download URL.
 */
export async function getPresignedUrl(
  attachmentId: string,
  expiryMinutes = 60
): Promise<{ url: string; expiryMinutes: number }> {
  return api.get(`/api/v1/files/${attachmentId}/url?expiryMinutes=${expiryMinutes}`);
}

/**
 * Get all attachments for a case.
 */
export async function getAttachmentsForCase(caseId: string): Promise<Attachment[]> {
  return api.get<Attachment[]>(`/api/v1/files/case/${caseId}`);
}

/**
 * Get attachments for a specific field in a case.
 */
export async function getAttachmentsForField(
  caseId: string,
  queryDefinitionId: string
): Promise<Attachment[]> {
  return api.get<Attachment[]>(`/api/v1/files/case/${caseId}/field/${queryDefinitionId}`);
}

/**
 * Delete an attachment.
 */
export async function deleteAttachment(attachmentId: string): Promise<void> {
  return api.delete(`/api/v1/files/${attachmentId}`);
}

/**
 * Link an attachment to a case.
 */
export async function linkAttachmentToCase(
  attachmentId: string,
  caseId: string
): Promise<Attachment> {
  return api.post<Attachment>(`/api/v1/files/${attachmentId}/link?caseId=${caseId}`);
}

/**
 * Get storage usage for a user.
 */
export async function getStorageUsage(userId: string): Promise<{
  bytesUsed: number;
  formatted: string;
}> {
  return api.get(`/api/v1/files/usage/${userId}`);
}

/**
 * Format file size for display.
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

/**
 * Get file type icon/emoji.
 */
export function getFileIcon(contentType: string): string {
  if (contentType.startsWith('image/')) return '🖼️';
  if (contentType === 'application/pdf') return '📄';
  if (contentType.includes('word')) return '📝';
  if (contentType.includes('excel') || contentType.includes('spreadsheet')) return '📊';
  if (contentType.startsWith('text/')) return '📃';
  return '📎';
}

// Map file extensions to MIME types
const extensionToMimeType: Record<string, string[]> = {
  '.pdf': ['application/pdf'],
  '.jpg': ['image/jpeg'],
  '.jpeg': ['image/jpeg'],
  '.png': ['image/png'],
  '.gif': ['image/gif'],
  '.webp': ['image/webp'],
  '.doc': ['application/msword'],
  '.docx': ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  '.xls': ['application/vnd.ms-excel'],
  '.xlsx': ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
  '.txt': ['text/plain'],
  '.csv': ['text/csv'],
};

const defaultAllowedMimeTypes = [
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'text/plain',
  'text/csv',
];

/**
 * Validate file before upload.
 */
export function validateFile(
  file: File,
  options?: {
    maxSizeMB?: number;
    allowedTypes?: string[];
  }
): { valid: boolean; error?: string } {
  const maxSize = (options?.maxSizeMB || 50) * 1024 * 1024;

  // Convert allowedTypes to MIME types if they are file extensions
  let allowedMimeTypes: string[];
  if (options?.allowedTypes && options.allowedTypes.length > 0) {
    allowedMimeTypes = [];
    for (const type of options.allowedTypes) {
      const trimmed = type.trim().toLowerCase();
      if (trimmed.startsWith('.')) {
        // It's a file extension, convert to MIME type(s)
        const mimes = extensionToMimeType[trimmed];
        if (mimes) {
          allowedMimeTypes.push(...mimes);
        }
      } else if (trimmed.includes('/')) {
        // It's already a MIME type
        allowedMimeTypes.push(trimmed);
      }
    }
    // If no valid types were found, use defaults
    if (allowedMimeTypes.length === 0) {
      allowedMimeTypes = defaultAllowedMimeTypes;
    }
  } else {
    allowedMimeTypes = defaultAllowedMimeTypes;
  }

  if (file.size > maxSize) {
    return {
      valid: false,
      error: `Filen är för stor. Max storlek är ${options?.maxSizeMB || 50} MB.`,
    };
  }

  if (!allowedMimeTypes.includes(file.type)) {
    return {
      valid: false,
      error: 'Filtypen stöds inte. Tillåtna typer: PDF, bilder, Word, Excel, text.',
    };
  }

  return { valid: true };
}
