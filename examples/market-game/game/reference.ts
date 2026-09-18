/**
 * The reference data: what can be traded, who is playing, and what each player is after.
 *
 * Game design, not user content — so unlike the ledger it is re-applied whenever it differs
 * from what the service holds. Edit an objective here and the next `rhc setup` writes it.
 */

/** Every collection the game uses, in one place, because the ACL has to name each of them. */
export const ITEMS_COLL = 'market_items';
export const PLAYERS_COLL = 'market_players';
export const OBJECTIVES_COLL = 'market_objectives';

export const ITEMS_META = {
  mcp: {
    enabled: true,
    description:
      'The four things that can be traded in this market. Reference data — it never changes ' +
      "during a game. 'coin' is an item like any other, so an offer can ask for goods, for " +
      'money, or for both in the same breath.',
    examples: [{ description: 'List every tradable item', action: 'query', args: {} }],
  },
};

export const ITEMS_SEED = [
  { _id: 'coin', name: 'Coin', note: 'Currency, and a target in its own right: every objective asks for some, so it is genuinely contested rather than a token nobody wants.' },
  { _id: 'grain', name: 'Grain', note: 'Held by trader1 (12) and trader2 (4).' },
  { _id: 'ore', name: 'Ore', note: 'Held by trader2 (12) and trader3 (4).' },
  { _id: 'silk', name: 'Silk', note: 'Held by trader3 (12) and trader1 (4).' },
];

export const PLAYERS_META = {
  mcp: {
    enabled: true,
    description:
      'Who is playing. The _id of a player is the user id the agent authenticates as, which ' +
      "is what makes a player's private objective private.",
    examples: [{ description: 'Who is in the game', action: 'query', args: {} }],
  },
};

export const PLAYERS_SEED = [
  { _id: 'trader1', name: 'Trader One', motto: 'Grain is a currency if you are patient.' },
  { _id: 'trader2', name: 'Trader Two', motto: 'I buy low and I am never in a hurry.' },
  { _id: 'trader3', name: 'Trader Three', motto: 'Cash is a position.' },
];

export const OBJECTIVES_META = {
  mcp: {
    enabled: true,
    description:
      'Your private objective — what you must own to win. The ACL puts a readFilter on this ' +
      'collection, so this same read returns a different single document to every player and ' +
      "there is no way to see anyone else's. The filter is applied by the server, identically " +
      'whether you read over REST or through MCP.',
    examples: [{ description: 'Read your own objective (you will only ever get yours)', action: 'query', args: {} }],
  },
};

/**
 * Cyclic on purpose. Every good is held by two players — one with 12, one with 4 — and each
 * objective asks for the good the other two hold, so nobody is a monopolist and nobody can sit
 * still. Coin is a target, not a token: every objective asks for 70 and everyone starts with 60,
 * so no one can buy their way to their goods and still meet the threshold. There are 180 coin
 * in the game and three thresholds of 70, so at most two players can hold enough at once.
 */
export const OBJECTIVES_SEED = [
  {
    _id: 'objective:trader1',
    player: 'trader1',
    goal: 'Own at least 10 ore and at least 70 coin.',
    requires: [{ item: 'ore', qty: 10 }, { item: 'coin', qty: 70 }],
    hint: 'trader2 holds most of the ore and wants silk, of which you have only 4 — trader3 has the rest. You start 10 coin short, so you cannot simply buy your way there: sell grain first.',
  },
  {
    _id: 'objective:trader2',
    player: 'trader2',
    goal: 'Own at least 10 silk and at least 70 coin.',
    requires: [{ item: 'silk', qty: 10 }, { item: 'coin', qty: 70 }],
    hint: 'trader3 holds most of the silk and wants grain, of which you have only 4 — trader1 has the rest. You start 10 coin short, so you cannot simply buy your way there: sell ore first.',
  },
  {
    _id: 'objective:trader3',
    player: 'trader3',
    goal: 'Own at least 10 grain and at least 70 coin.',
    requires: [{ item: 'grain', qty: 10 }, { item: 'coin', qty: 70 }],
    hint: 'trader1 holds most of the grain and wants ore, of which you have only 4 — trader2 has the rest. You start 10 coin short, so you cannot simply buy your way there: sell silk first.',
  },
];

/** The three players. They are not accounts: see {@link TABLE_USER}. */
export const TRADERS = ['trader1', 'trader2', 'trader3'] as const;

/**
 * One account for everybody, because that is what a host gives you: an MCP connector carries a
 * single identity for the whole application, so three agents behind it are one player with three
 * voices — and this game is built on knowing who wrote what.
 *
 * So the account is nobody in particular. On its own it may read the public game and nothing
 * else. Which player is speaking comes from two arguments on the call, `trader` and `secret`: the
 * ACL holds one rule per pair, and a call is that player only when both match. The server then
 * stamps `actor` from the rule, so what a player signs is decided by the secret they proved and
 * not by anything they sent.
 *
 * Each agent is told its own secret and no other. That is the whole of the separation.
 *
 * Everything here is in the open on purpose: it is a game, and these are meant to be copied into
 * prompts. Read none of it as a pattern. A secret in a query string is read by every request log
 * and by whoever holds the transcript; when a client can hold a credential of its own, give it an
 * account of its own.
 */
export const TABLE_USER = 'table';
export const TABLE_ROLE = 'table';
export const TABLE_PASSWORD = 'Aged-Harbour-Kettle-7';

export const SECRETS: Record<string, string> = {
  trader1: 'seagull-brick-oath',
  trader2: 'copper-lantern-drift',
  trader3: 'velvet-anchor-moss',
};
