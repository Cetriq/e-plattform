import { api } from './client';

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
  downloadUrl: string;
}

export interface UploadProgress {
  loaded: number;
  total: number;
  percentage: number;
}

/**
 * Upload a file.
 */
export async function uploadFile(
  file: File,
  userId: string,
  options?: {
    caseId?: string;
    queryDefinitionId?: string;
    onProgress?: (progress: UploadProgress) => void;
  }
): Promise<Attachment> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('userId', userId);

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
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText));
      } else {
        reject(new Error(`Upload failed: ${xhr.statusText}`));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Upload failed'));
    });

    xhr.open('POST', `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/v1/files`);
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
  return `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/v1/files/${attachmentId}/download`;
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
export async function deleteAttachment(attachmentId: string, userId: string): Promise<void> {
  return api.delete(`/api/v1/files/${attachmentId}?userId=${userId}`);
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
