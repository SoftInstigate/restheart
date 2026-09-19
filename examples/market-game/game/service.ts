/**
 * The few things both setups need to talk to a service: compare what is there with what is meant,
 * and write only when they differ.
 */
import { isApiError } from '@restheart-cloud/cli';
import type { ServiceClient } from '@restheart-cloud/cli';

export type Doc = Record<string, unknown>;

/** Keys sorted at every level, so two documents compare by content and not by key order. */
function sortKeys(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(sortKeys);
  if (v && typeof v === 'object') {
    return Object.fromEntries(
      Object.keys(v as Doc).sort().map(k => [k, sortKeys((v as Doc)[k])])
    );
  }
  return v;
}

export const canonical = (v: unknown) => JSON.stringify(sortKeys(v));

/** What the service holds at `path`, without what it adds on its own; null when nothing is there. */
export async function stored(service: ServiceClient, path: string): Promise<Doc | null> {
  try {
    const res = await service.fetch(path);
    const { _etag, _id, ...doc } = (await res.json()) as Doc;
    return doc;
  } catch (err) {
    if (isApiError(err) && err.status === 404) return null;
    throw err;
  }
}

/**
 * Does the service hold `desired` at `path` — every key of it, as written here?
 *
 * Keys the service holds that this file does not mention are left out of the comparison: the
 * console may add its own, and a step must not report drift over something it never set.
 */
export async function holds(service: ServiceClient, path: string, desired: Doc): Promise<boolean> {
  const doc = await stored(service, path);
  return doc !== null && Object.keys(desired).every(k => canonical(doc[k]) === canonical(desired[k]));
}

export const json = (body: unknown) => JSON.stringify(body);

/** PUT a document by id, creating it if it is not there. */
export const put = (service: ServiceClient, path: string, body: unknown) =>
  service.fetch(`${path}?wm=upsert`, { method: 'PUT', body: json(body) });

/** DELETE a document by id, and take "it was not there" for done. */
export async function remove(service: ServiceClient, path: string): Promise<void> {
  try {
    await service.fetch(path, { method: 'DELETE' });
  } catch (err) {
    if (!isApiError(err) || err.status !== 404) throw err;
  }
}
