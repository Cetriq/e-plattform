'use client';

import React, { useRef, useState, useCallback } from 'react';
import type { QueryDefinition } from '../types';
import { useField } from '../FormContext';
import { FieldWrapper } from './FieldWrapper';
import {
  uploadFile,
  deleteAttachment,
  validateFile,
  formatFileSize,
  getFileIcon,
  type Attachment,
  type UploadProgress,
} from '@/lib/api/files';

interface FileFieldProps {
  query: QueryDefinition;
  userId?: string;
  caseId?: string;
}

interface UploadingFile {
  file: File;
  progress: number;
  error?: string;
}

export function FileField({ query, userId, caseId }: FileFieldProps) {
  const { value, onChange, onBlur, state } = useField(query.id);
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState<UploadingFile[]>([]);
  const [dragOver, setDragOver] = useState(false);

  // Value is an array of Attachment objects
  const attachments = (value as Attachment[]) || [];

  const handleUpload = useCallback(async (files: File[]) => {
    // Validate files first
    const validFiles: File[] = [];
    const invalidFiles: { file: File; error: string }[] = [];

    for (const file of files) {
      const validation = validateFile(file, {
        maxSizeMB: query.config.maxSize ? query.config.maxSize / (1024 * 1024) : undefined,
        allowedTypes: query.config.accept?.split(',').map(t => t.trim()),
      });

      if (validation.valid) {
        validFiles.push(file);
      } else {
        invalidFiles.push({ file, error: validation.error || 'Okänt fel' });
      }
    }

    // Show validation errors
    if (invalidFiles.length > 0) {
      setUploading(prev => [
        ...prev,
        ...invalidFiles.map(({ file, error }) => ({
          file,
          progress: 0,
          error,
        })),
      ]);
      // Clear errors after 5 seconds
      setTimeout(() => {
        setUploading(prev => prev.filter(u => !u.error));
      }, 5000);
    }

    // Upload valid files
    for (const file of validFiles) {
      setUploading(prev => [...prev, { file, progress: 0 }]);

      try {
        const attachment = await uploadFile(
          file,
          userId || 'anonymous',
          {
            caseId,
            queryDefinitionId: query.id,
            onProgress: (progress: UploadProgress) => {
              setUploading(prev =>
                prev.map(u =>
                  u.file === file ? { ...u, progress: progress.percentage } : u
                )
              );
            },
          }
        );

        // Remove from uploading and add to attachments
        setUploading(prev => prev.filter(u => u.file !== file));

        const newAttachments = query.config.multiple
          ? [...attachments, attachment]
          : [attachment];
        onChange(newAttachments);
      } catch (error) {
        setUploading(prev =>
          prev.map(u =>
            u.file === file
              ? { ...u, error: error instanceof Error ? error.message : 'Uppladdning misslyckades' }
              : u
          )
        );
        // Clear error after 5 seconds
        setTimeout(() => {
          setUploading(prev => prev.filter(u => u.file !== file));
        }, 5000);
      }
    }
  }, [attachments, caseId, onChange, query.config.accept, query.config.maxSize, query.config.multiple, query.id, userId]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = e.target.files;
    if (!selectedFiles || selectedFiles.length === 0) return;
    handleUpload(Array.from(selectedFiles));
    // Reset input so same file can be selected again
    e.target.value = '';
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);

    const droppedFiles = e.dataTransfer.files;
    if (!droppedFiles || droppedFiles.length === 0) return;
    handleUpload(Array.from(droppedFiles));
  };

  const handleRemove = async (attachment: Attachment) => {
    try {
      await deleteAttachment(attachment.id, userId || 'anonymous');
      onChange(attachments.filter(a => a.id !== attachment.id));
    } catch (error) {
      console.error('Failed to delete attachment:', error);
      // Still remove from UI even if backend delete fails
      onChange(attachments.filter(a => a.id !== attachment.id));
    }
  };

  const cancelUpload = (file: File) => {
    setUploading(prev => prev.filter(u => u.file !== file));
  };

  const isDisabled = state === 'DISABLED';
  const isImageOnly = query.queryType === 'IMAGE';

  return (
    <FieldWrapper query={query}>
      <div className="space-y-3">
        {/* Drop zone */}
        <div
          className={`
            border-2 border-dashed rounded-lg p-6 transition-colors cursor-pointer
            ${dragOver
              ? 'border-blue-500 bg-blue-50'
              : 'border-gray-300 hover:border-blue-400 bg-gray-50'
            }
            ${isDisabled ? 'opacity-50 cursor-not-allowed' : ''}
          `}
          onClick={() => !isDisabled && inputRef.current?.click()}
          onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
        >
          <div className="text-center">
            <svg
              className={`mx-auto h-10 w-10 ${dragOver ? 'text-blue-500' : 'text-gray-400'}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
              />
            </svg>
            <p className="mt-2 text-sm text-gray-600">
              {dragOver
                ? 'Släpp filen här'
                : 'Klicka för att välja fil eller dra och släpp'
              }
            </p>
            {isImageOnly ? (
              <p className="mt-1 text-xs text-gray-400">
                Tillåtna format: JPG, PNG, GIF, WebP
              </p>
            ) : query.config.accept ? (
              <p className="mt-1 text-xs text-gray-400">
                Tillåtna format: {query.config.accept}
              </p>
            ) : (
              <p className="mt-1 text-xs text-gray-400">
                PDF, bilder, Word, Excel, text (max 50 MB)
              </p>
            )}
          </div>
          <input
            ref={inputRef}
            type="file"
            onChange={handleChange}
            onBlur={onBlur}
            disabled={isDisabled}
            accept={isImageOnly ? 'image/*' : query.config.accept}
            multiple={query.config.multiple}
            className="hidden"
          />
        </div>

        {/* Uploading files */}
        {uploading.length > 0 && (
          <ul className="space-y-2">
            {uploading.map((upload, i) => (
              <li
                key={`uploading-${i}`}
                className={`
                  flex items-center gap-3 px-4 py-3 rounded-lg border
                  ${upload.error ? 'bg-red-50 border-red-200' : 'bg-blue-50 border-blue-200'}
                `}
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-700 truncate">
                    {upload.file.name}
                  </p>
                  {upload.error ? (
                    <p className="text-xs text-red-600">{upload.error}</p>
                  ) : (
                    <div className="mt-1 flex items-center gap-2">
                      <div className="flex-1 h-1.5 bg-blue-200 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-blue-600 transition-all duration-300"
                          style={{ width: `${upload.progress}%` }}
                        />
                      </div>
                      <span className="text-xs text-blue-600 font-medium">
                        {upload.progress}%
                      </span>
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => cancelUpload(upload.file)}
                  className="text-gray-400 hover:text-gray-600 p-1"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        )}

        {/* Uploaded attachments */}
        {attachments.length > 0 && (
          <ul className="divide-y divide-gray-200 border rounded-lg overflow-hidden">
            {attachments.map((attachment) => (
              <li
                key={attachment.id}
                className="flex items-center justify-between px-4 py-3 bg-white hover:bg-gray-50"
              >
                <div className="flex items-center gap-3 min-w-0">
                  {attachment.isImage ? (
                    <div className="w-10 h-10 rounded bg-gray-100 flex items-center justify-center overflow-hidden">
                      <img
                        src={attachment.downloadUrl}
                        alt={attachment.originalFilename}
                        className="w-full h-full object-cover"
                      />
                    </div>
                  ) : (
                    <div className="w-10 h-10 rounded bg-gray-100 flex items-center justify-center text-lg">
                      {getFileIcon(attachment.contentType)}
                    </div>
                  )}
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-700 truncate">
                      {attachment.originalFilename}
                    </p>
                    <p className="text-xs text-gray-400">
                      {attachment.fileSizeFormatted || formatFileSize(attachment.fileSize)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <a
                    href={attachment.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-blue-500 hover:text-blue-700 p-1"
                    title="Ladda ner"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                    </svg>
                  </a>
                  {!isDisabled && (
                    <button
                      type="button"
                      onClick={() => handleRemove(attachment)}
                      className="text-red-500 hover:text-red-700 p-1"
                      title="Ta bort"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </FieldWrapper>
  );
}
