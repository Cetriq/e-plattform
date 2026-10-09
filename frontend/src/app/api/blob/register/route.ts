import { del, head } from '@vercel/blob';
import { backendError, backendFetch } from '@/lib/server/backend';

interface RegisterRequest {
  pathname: string;
  originalFilename: string;
  caseId: string;
  queryDefinitionId?: string;
}

/**
 * Records a file the browser has uploaded to Blob. Size and content type are
 * read from the store rather than trusted from the browser.
 */
export async function POST(request: Request) {
  const authorization = request.headers.get('authorization');
  const { pathname, originalFilename, caseId, queryDefinitionId } = (await request.json()) as RegisterRequest;

  let stored;
  try {
    stored = await head(pathname);
  } catch {
    return Response.json({ error: 'Filen hittades inte i fillagringen' }, { status: 400 });
  }

  const response = await backendFetch('/api/v1/files/register', authorization, {
    method: 'POST',
    body: JSON.stringify({
      pathname: stored.pathname,
      originalFilename,
      contentType: stored.contentType,
      fileSize: stored.size,
      caseId,
      queryDefinitionId,
    }),
  });

  if (!response.ok) {
    // Don't keep files the backend refused
    await del(stored.pathname).catch(() => undefined);
    return Response.json({ error: await backendError(response) }, { status: response.status });
  }
  return Response.json(await response.json());
}
