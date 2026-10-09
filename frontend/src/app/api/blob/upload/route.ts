import { handleUpload, type HandleUploadBody } from '@vercel/blob/client';
import { backendError, backendFetch } from '@/lib/server/backend';

/**
 * Hands out short-lived tokens that let the browser upload a file straight to
 * the private Blob store. The backend decides whether the user may add files
 * to the case and which file types and sizes are allowed.
 */
export async function POST(request: Request) {
  const body = (await request.json()) as HandleUploadBody;
  const authorization = request.headers.get('authorization');

  try {
    const result = await handleUpload({
      body,
      request,
      onBeforeGenerateToken: async (pathname, clientPayload) => {
        const { caseId } = JSON.parse(clientPayload ?? '{}') as { caseId?: string };
        const response = await backendFetch('/api/v1/files/upload-permission', authorization, {
          method: 'POST',
          body: JSON.stringify({ caseId }),
        });
        if (!response.ok) {
          throw new Error(await backendError(response));
        }
        const permission = (await response.json()) as {
          pathnamePrefix: string;
          maxFileSize: number;
          allowedContentTypes: string[];
        };
        if (!pathname.startsWith(permission.pathnamePrefix) || pathname.includes('..')) {
          throw new Error('Ogiltigt filnamn');
        }
        return {
          allowedContentTypes: permission.allowedContentTypes,
          maximumSizeInBytes: permission.maxFileSize,
          addRandomSuffix: true,
        };
      },
    });
    return Response.json(result);
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : 'Uppladdningen nekades' },
      { status: 400 }
    );
  }
}
