import { get } from '@vercel/blob';
import { backendError, backendFetch } from '@/lib/server/backend';

/**
 * Streams a file from the private Blob store after the backend has confirmed
 * that the user may read it.
 */
export async function GET(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const authorization = request.headers.get('authorization');

  const response = await backendFetch(`/api/v1/files/${encodeURIComponent(id)}`, authorization);
  if (!response.ok) {
    return Response.json({ error: await backendError(response) }, { status: response.status });
  }
  const attachment = (await response.json()) as {
    originalFilename: string;
    contentType: string;
    blobPathname: string | null;
  };
  if (!attachment.blobPathname) {
    return Response.json({ error: 'Filen finns inte i fillagringen' }, { status: 404 });
  }

  const file = await get(attachment.blobPathname, { access: 'private' });
  if (!file || file.statusCode !== 200) {
    return Response.json({ error: 'Filen hittades inte' }, { status: 404 });
  }

  return new Response(file.stream, {
    headers: {
      'Content-Type': attachment.contentType || file.blob.contentType,
      'Content-Length': String(file.blob.size),
      'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(attachment.originalFilename)}`,
      'Cache-Control': 'private, no-store',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}
