/**
 * Who may do what. There is no code anywhere enforcing it: this is the whole security model.
 *
 * One account plays for everybody (see TABLE_USER in reference.ts). On its own it reads the
 * public game. Two things are private — appending to the ledger as a player, and reading that
 * player's objective — and each has one rule per player, holding that player's name and secret.
 * A call is that player only when both match.
 */
import { LEDGER } from './ledger.ts';
import { ITEMS_COLL, OBJECTIVES_COLL, PLAYERS_COLL, SECRETS, TABLE_ROLE, TRADERS } from './reference.ts';

const roles = [TABLE_ROLE];

export const PERMISSIONS: Record<string, Record<string, unknown>> = {
  /**
   * Reaching the MCP endpoint is one permission; reading the data behind it is another. They
   * are deliberately separate: a caller welcome at /mcp is still refused anything its ACL does
   * not grant.
   *
   * All three methods, and POST alone is a trap: POST carries the JSON-RPC messages, but GET
   * opens the stream on which the server delivers notifications/resources/updated, and DELETE
   * closes the session. With POST only, a resources/subscribe succeeds and the client then
   * waits forever for notifications it can never receive.
   */
  canReachMcp: {
    predicate: "path-prefix('/mcp') and method(POST, GET, DELETE)",
    roles,
    priority: 100,
  },

  /**
   * The public game: items, players, the ledger and its aggregations. No secret is asked for
   * here on purpose. A catalogue is built by asking the ACL what a caller may read, and that
   * question is asked without arguments, so anything behind a secret would simply not appear in
   * it. The public half stays discoverable; the two private things are in the prompt.
   *
   * path-prefix, not path: one collection is reachable at /x, /x/_size, /x/{id} and
   * /x/_aggrs/{name}, and an exact path() would cover only the first. And the prefixes are
   * spelled out one by one because path-prefix matches whole SEGMENTS: path-prefix('/market_')
   * matches nothing at all, since no path has a segment equal to 'market_'.
   */
  canReadTheMarket: {
    predicate: `path-prefix('/${ITEMS_COLL}', '/${PLAYERS_COLL}', '/${LEDGER}') and method(GET)`,
    roles,
    priority: 100,
  },

  /** The GraphQL app is a POST to /graphql/market. */
  canQueryTheMarketGraph: {
    predicate: "path('/graphql/market') and method(POST)",
    roles,
    priority: 100,
  },

  ...Object.fromEntries(TRADERS.flatMap(player => {
    // Both values are written here, in one predicate: there is no table of secrets to look up,
    // and nothing is compared at runtime except these two equalities. An argument that is not
    // sent reads as empty and matches neither, so leaving one out is a refusal and not a way past.
    //
    // `%{q,name}` is Undertow's own query-parameter attribute, read when the predicate runs.
    // Not RESTHeart's `@qparams['name']`: that one is substituted into the predicate *text*
    // before it is parsed, and a permission is parsed once when it is loaded, when the brackets
    // are still there — Undertow uses brackets for its own parameter lists and refuses the
    // string, so the permission is dropped and every request it should have allowed is a 403.
    const proves = `equals(%{q,trader}, '${player}') and equals(%{q,secret}, '${SECRETS[player]}')`;

    return [
      /**
       * Every player issues the same read and gets their own objective. The filter names the
       * player the secret proved, and a ?filter of the caller's own is intersected with it,
       * never replaces it.
       */
      [`readsObjectiveOf_${player}`, {
        predicate: `path-prefix('/${OBJECTIVES_COLL}') and method(GET) and ${proves}`,
        roles,
        priority: 1000,
        mongo: { readFilter: { player } },
      }],

      /**
       * Writing is POST to the ledger and nothing else. No PATCH, no DELETE, no other
       * collection: the game has no mutable state to corrupt. Publishing an offer, accepting one
       * and claiming victory are all the same operation, appending an event.
       *
       * `actor` is stamped from here, which is what makes the ledger trustworthy without any
       * code: sending somebody else's name in the body changes nothing, and neither does sending
       * none. `ts` too, because the board orders events by it and a client-supplied time would
       * let a player rewrite the order of history.
       */
      [`appendsAs_${player}`, {
        predicate: `path('/${LEDGER}') and method(POST) and ${proves}`,
        roles,
        priority: 1000,
        mongo: { mergeRequest: { actor: player, ts: '@now' } },
      }],
    ];
  })),
};
