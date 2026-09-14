/**
 * What a trader may do. Five permissions, and the game's whole security model is in them:
 * there is no code anywhere enforcing who may write what.
 */
import { LEDGER } from './ledger.ts';
import { ITEMS_COLL, OBJECTIVES_COLL, PLAYERS_COLL, TRADER_ROLE } from './reference.ts';

const roles = [TRADER_ROLE];

export const PERMISSIONS: Record<string, Record<string, unknown>> = {
  /**
   * Reaching the MCP endpoint is one permission; reading the data behind it is another. They
   * are deliberately separate: a trader welcome at /mcp is still refused anything its ACL does
   * not grant.
   *
   * All three methods, and POST alone is a trap: POST carries the JSON-RPC messages, but GET
   * opens the stream on which the server delivers notifications/resources/updated, and DELETE
   * closes the session. With POST only, a resources/subscribe succeeds and the client then
   * waits forever for notifications it can never receive.
   */
  traderCanReachMcp: {
    predicate: "path-prefix('/mcp') and method(POST, GET, DELETE)",
    roles,
    priority: 100,
  },

  /**
   * Read access to the PUBLIC game state: items, players, the ledger and its aggregations.
   *
   * market_objectives is deliberately NOT here — it has its own permission below. Two
   * permissions matching the same request are resolved by priority, and a security boundary
   * that depends on getting a priority number the right way round is a trap: leave exactly one
   * permission matching the private collection.
   *
   * path-prefix, not path: one collection is reachable at /x, /x/_size, /x/{id} and
   * /x/_aggrs/{name}, and an exact path() would cover only the first. And the prefixes are
   * spelled out one by one because path-prefix matches whole SEGMENTS: path-prefix('/market_')
   * matches nothing at all, since no path has a segment equal to 'market_'.
   */
  traderCanReadTheMarket: {
    predicate: `path-prefix('/${ITEMS_COLL}', '/${PLAYERS_COLL}', '/${LEDGER}') and method(GET)`,
    roles,
    priority: 100,
  },

  /**
   * Every trader issues the same read and gets a different single document. The filter is
   * applied by the server; a caller cannot supply it, and neither a ?filter nor a direct GET of
   * another player's document gets around it. It holds identically over REST and through MCP.
   *
   * Two spellings of "me", because a trader can arrive two ways: signed in with a password,
   * where the account is the user document and `@user._id` is its id; or with a token from the
   * service's own sign-in page, where the account is the token's claims and the id is `sub`.
   * A variable that does not resolve matches nothing, so the branch that does not apply is
   * simply empty — and neither branch can ever name somebody else.
   */
  traderCanReadOwnObjectiveOnly: {
    predicate: `path-prefix('/${OBJECTIVES_COLL}') and method(GET)`,
    roles,
    priority: 1000,
    mongo: { readFilter: { $or: [{ player: '@user._id' }, { player: '@user.sub' }] } },
  },

  /**
   * Writing is POST to the ledger and nothing else. No PATCH, no DELETE, no other collection:
   * the game has no mutable state to corrupt. Publishing an offer, accepting one and claiming
   * victory are all the same operation — appending an event.
   *
   * mergeRequest is what makes the ledger trustworthy without any code. The server overwrites
   * `actor` with the authenticated user, so a player cannot publish an offer in someone else's
   * name or claim victory on their behalf. And it stamps `ts` itself, which matters because the
   * board orders events by it: a client-supplied time would let a player rewrite the order of
   * history.
   */
  traderCanAppendToTheLedger: {
    predicate: `path('/${LEDGER}') and method(POST)`,
    roles,
    priority: 100,
    mongo: { mergeRequest: { actor: '@user._id', ts: '@now' } },
  },

  /** The GraphQL app is a POST to /graphql/market. */
  traderCanQueryTheMarketGraph: {
    predicate: "path('/graphql/market') and method(POST)",
    roles,
    priority: 100,
  },
};
