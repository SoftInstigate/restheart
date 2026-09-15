/**
 * What the market game needs from a RESTHeart Cloud service.
 *
 *   rhc setup --srv <srvId>              # configure, or confirm nothing is missing
 *   rhc setup --srv <srvId> --dry-run    # only say what is missing
 *   rhc setup --srv <srvId> --force game # wipe the ledger and start a fresh game
 *
 * Every step is a `check` and an `apply`. Against a configured service this writes nothing
 * and reports each step satisfied — which is what makes it safe to run after every edit.
 *
 * The checks compare *content*, not presence: a permission whose predicate you changed, an
 * aggregation you rewrote, an objective you re-worded all show up as work outstanding rather
 * than as satisfied. The one exception is the ledger itself, which is a game in progress and
 * is never touched unless you ask — see the last step.
 *
 * The trader passwords are the only secrets, named as TRADER1_PASSWORD, TRADER2_PASSWORD and
 * TRADER3_PASSWORD, and only read the first time: an account that exists is left alone.
 */
import { defineSetup, step, fromEnv, isApiError } from '@restheart-cloud/cli';
import type { ServiceClient } from '@restheart-cloud/cli';

import { MARKET_EVENT_SCHEMA } from './game/schema.ts';
import { GENESIS, LEDGER, LEDGER_META } from './game/ledger.ts';
import { CONSTRAINTS } from './game/rules.ts';
import {
  ITEMS_COLL, ITEMS_META, ITEMS_SEED,
  PLAYERS_COLL, PLAYERS_META, PLAYERS_SEED,
  OBJECTIVES_COLL, OBJECTIVES_META, OBJECTIVES_SEED,
  TRADERS, TRADER_ROLE,
} from './game/reference.ts';
import { PERMISSIONS } from './game/acl.ts';
import { GRAPHQL_APP, graphqlApp } from './game/graphql.ts';

const SCHEMA_ID = 'marketEvent';

type Doc = Record<string, unknown>;

// ── Comparing what is there with what is meant ────────────────────────────────

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

const canonical = (v: unknown) => JSON.stringify(sortKeys(v));

/** What the service holds at `path`, without what it adds on its own; null when nothing is there. */
async function stored(service: ServiceClient, path: string): Promise<Doc | null> {
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
async function holds(service: ServiceClient, path: string, desired: Doc): Promise<boolean> {
  const doc = await stored(service, path);
  return doc !== null && Object.keys(desired).every(k => canonical(doc[k]) === canonical(desired[k]));
}

const json = (body: unknown) => JSON.stringify(body);

/** PUT a document by id, creating it if it is not there. */
const put = (service: ServiceClient, path: string, body: unknown) =>
  service.fetch(`${path}?wm=upsert`, { method: 'PUT', body: json(body) });

// ── Collections that carry metadata ───────────────────────────────────────────

/**
 * A collection with its metadata — the MCP block, and for the ledger the schema reference,
 * the constraint, the aggregations and the stream. Created with the metadata when missing;
 * patched when it exists and differs. PATCH merges keys, so a key this file does not set is
 * left as the console left it.
 */
const collection = (name: string, meta: Doc, what: string) =>
  step(`${name}: ${what}`, {
    check: ({ service }) => holds(service, `/${name}/_meta`, meta),
    async apply({ service }) {
      if (await service.collectionExists(name)) {
        await service.fetch(`/${name}`, { method: 'PATCH', body: json(meta) });
      } else {
        await service.createCollection(name, meta);
      }
    },
  });

/**
 * Documents that are game design rather than play — items, players, objectives. Re-applied
 * whenever they differ from what is stored, so an edited objective reaches the service on
 * the next run. A document the seed no longer names is not removed; drop the collection from
 * the console if you rename one.
 */
const seeded = (name: string, docs: Doc[]) =>
  step(`${name}: the data`, {
    async check({ service }) {
      for (const { _id, ...doc } of docs) {
        if (!(await holds(service, `/${name}/${_id}`, doc))) return false;
      }
      return true;
    },
    async apply({ service }) {
      // Whole documents, `_id` included, the same as the ledger: a collection may carry a
      // schema that wants it in the body.
      for (const doc of docs) {
        await put(service, `/${name}/${doc._id}`, doc);
      }
    },
  });

// ── The game ──────────────────────────────────────────────────────────────────

const genesisFilter = encodeURIComponent(json({ type: 'genesis' }));

/**
 * An empty filter is refused, so "everything" has to be spelled out.
 */
const everything = encodeURIComponent(json({ _id: { $exists: true } }));

export default defineSetup('Market game', [
  step('marketEvent schema stored', {
    check: ({ service }) => holds(service, `/_schemas/${SCHEMA_ID}`, MARKET_EVENT_SCHEMA),
    apply: ({ service }) => service.putSchema(SCHEMA_ID, MARKET_EVENT_SCHEMA),
  }),

  // After the schema, which the ledger's metadata refers to by id.
  collection(LEDGER, { ...LEDGER_META, constraints: CONSTRAINTS }, 'schema, rules, aggregations, stream and MCP'),
  collection(ITEMS_COLL, ITEMS_META, 'published over MCP'),
  collection(PLAYERS_COLL, PLAYERS_META, 'published over MCP'),
  collection(OBJECTIVES_COLL, OBJECTIVES_META, 'published over MCP'),

  seeded(ITEMS_COLL, ITEMS_SEED),
  seeded(PLAYERS_COLL, PLAYERS_SEED),
  seeded(OBJECTIVES_COLL, OBJECTIVES_SEED),

  step('the three trader accounts', {
    // Presence only, on purpose: a password is set once and never compared, because the
    // service does not hand it back. To rotate one, force this step with the new value in
    // the environment.
    async check({ service }) {
      for (const id of TRADERS) {
        if (!(await service.userExists(id))) return false;
      }
      return true;
    },
    async apply({ service }) {
      for (const id of TRADERS) {
        if (await service.userExists(id)) continue;
        // The users collection of a cloud service carries the accounts schema, which requires
        // `_id`, `password`, `roles` and a `profile` with a name and a surname — `_id` in the
        // body as well as in the path, since a PUT arrives without it otherwise. The profile is
        // the player's display name split in two, so a trader reads the same everywhere.
        //
        // The service checks password strength on this write, with the same rule it applies
        // to sign-up. A weak one is refused with 400 and the reason; pick something long.
        const [name, ...surname] = (PLAYERS_SEED.find(p => p._id === id)?.name ?? id).split(' ');
        await service.createUser(id, {
          _id: id,
          password: fromEnv(`${id.toUpperCase()}_PASSWORD`),
          roles: [TRADER_ROLE],
          profile: { name, surname: surname.join(' ') || name },
        });
      }
    },
  }),

  // One step per permission, and each compares the whole document: a predicate or a filter
  // edited under the same id is drift, not a satisfied step. Two of these are the game's
  // security boundary — the readFilter that hides the objectives, the mergeRequest that pins
  // `actor` — and a check that cannot see them change would make a hole invisible.
  ...Object.entries(PERMISSIONS).map(([id, doc]) =>
    step(`permission ${id}`, {
      check: ({ service }) => holds(service, `/acl/${id}`, doc),
      apply: ({ service }) => service.putPermission(id, doc),
    })
  ),

  step('the market GraphQL app', {
    check: ({ service, srvId }) => holds(service, `/gql-apps/${GRAPHQL_APP}`, graphqlApp(srvId)),
    apply: ({ service, srvId }) => put(service, `/gql-apps/${GRAPHQL_APP}`, graphqlApp(srvId)),
  }),

  step('a game on the board (force this step for a fresh one)', {
    /**
     * The only step that touches play rather than configuration, and the check is deliberately
     * shallow: it asks whether the three starting endowments are on the ledger, not whether the
     * ledger holds nothing else. A game in progress is satisfied, and a re-run after editing a
     * permission does not wipe it.
     *
     * To start over: `rhc setup --srv <srvId> --force game`. The apply wipes the ledger and
     * seeds it again; the re-check then finds the endowments and reports it applied.
     */
    async check({ service }) {
      if (!(await service.collectionExists(LEDGER))) return false;
      const res = await service.fetch(`/${LEDGER}?filter=${genesisFilter}&pagesize=10`);
      const ids = new Set(((await res.json()) as Doc[]).map(d => d._id));
      return GENESIS.every(g => ids.has(g._id));
    },
    async apply({ service }) {
      try {
        await service.fetch(`/${LEDGER}/*?filter=${everything}`, { method: 'DELETE' });
      } catch (err) {
        // Nothing there to wipe is not a failure.
        if (!isApiError(err) || err.status !== 404) throw err;
      }
      // The whole document, `_id` included: the marketEvent schema requires it in the body, and
      // a PUT arrives with it only in the path otherwise.
      for (const doc of GENESIS) {
        await put(service, `/${LEDGER}/${doc._id}`, doc);
      }
    },
  }),
]);
