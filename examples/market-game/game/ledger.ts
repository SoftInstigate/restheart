/**
 * The ledger: what `market_events` carries besides its documents.
 *
 * Everything the game "stores" — who owns what, which offers are open, who won — is derived
 * from this one append-only collection. The derivation is written once here and used by the
 * `board` and `holdings` aggregations below, and by every rule in rules.ts. One copy, because
 * several copies of two hundred lines of pipeline is how an aggregation and the rule that
 * guards it end up disagreeing.
 */
import { ITEMS } from './schema.ts';

// ── The derivation ────────────────────────────────────────────────────────────

/**
 * One document per offer id, with the first event of each type pulled out by name.
 *
 * `$eq: ["$field", null]` is false when the field is missing, in expressions, so the stages
 * below test `$type` instead. Inside a `$match` the query semantics differ and `$ne: null` is
 * fine — see the board's facets.
 */
const firstOfType = (type: string) => ({
  $first: { $filter: { input: '$events', as: 'e', cond: { $eq: ['$$e.type', type] } } },
});

const isObject = (field: string) => ({ $eq: [{ $type: field }, 'object'] });

export const GROUPED_BY_OFFER = [
  { $group: { _id: '$offerId', events: { $push: '$$ROOT' } } },
  {
    $addFields: {
      genesis: firstOfType('genesis'),
      offer: firstOfType('offer'),
      trade: firstOfType('trade'),
      claim: firstOfType('claim'),
    },
  },
];

/**
 * Every settled trade becomes four movements (−give to the giver, +give to the taker, −want to
 * the taker, +want to the giver) and every genesis one per grant. An offer nobody has accepted
 * yet is a commitment: the giver still owns the goods but has promised them.
 */
export const MOVEMENTS = [
  {
    $addFields: {
      movements: {
        $concatArrays: [
          {
            $cond: [
              isObject('$genesis'),
              {
                $map: {
                  input: '$genesis.grants',
                  as: 'g',
                  in: { player: '$genesis.actor', item: '$$g.item', qty: '$$g.qty' },
                },
              },
              [],
            ],
          },
          {
            $cond: [
              { $and: [isObject('$offer'), isObject('$trade')] },
              [
                { player: '$offer.actor', item: '$offer.give.item', qty: { $multiply: [-1, '$offer.give.qty'] } },
                { player: '$trade.actor', item: '$offer.give.item', qty: '$offer.give.qty' },
                { player: '$trade.actor', item: '$offer.want.item', qty: { $multiply: [-1, '$offer.want.qty'] } },
                { player: '$offer.actor', item: '$offer.want.item', qty: '$offer.want.qty' },
              ],
              [],
            ],
          },
        ],
      },
      commitments: {
        $cond: [
          { $and: [isObject('$offer'), { $ne: [{ $type: '$trade' }, 'object'] }] },
          [{ player: '$offer.actor', item: '$offer.give.item', qty: '$offer.give.qty' }],
          [],
        ],
      },
    },
  },
  {
    $addFields: {
      entries: {
        $concatArrays: [
          {
            $map: {
              input: '$movements',
              as: 'm',
              in: { player: '$$m.player', item: '$$m.item', owned: '$$m.qty', committed: 0 },
            },
          },
          {
            $map: {
              input: '$commitments',
              as: 'c',
              in: { player: '$$c.player', item: '$$c.item', owned: 0, committed: '$$c.qty' },
            },
          },
        ],
      },
    },
  },
];

/** One row per (player, item): owned, committed to open offers, and what is left to promise. */
export const PER_PLAYER_ITEM = [
  { $unwind: '$entries' },
  {
    $group: {
      _id: { player: '$entries.player', item: '$entries.item' },
      qty: { $sum: '$entries.owned' },
      committed: { $sum: '$entries.committed' },
    },
  },
  { $addFields: { available: { $subtract: ['$qty', '$committed'] } } },
];

/** Rows folded into one document per player, goods sorted by item. */
const BY_PLAYER = [
  { $sort: { '_id.item': 1 } },
  {
    $group: {
      _id: '$_id.player',
      goods: { $push: { item: '$_id.item', qty: '$qty', committed: '$committed', available: '$available' } },
    },
  },
];

/** The whole derivation, from the raw ledger to one row per (player, item). */
export const HOLDINGS = [...GROUPED_BY_OFFER, ...MOVEMENTS, ...PER_PLAYER_ITEM];

// ── The aggregations ──────────────────────────────────────────────────────────

const board = {
  uri: 'board',
  type: 'pipeline',
  stages: [
    ...GROUPED_BY_OFFER,
    ...MOVEMENTS,
    {
      $facet: {
        holdings: [...PER_PLAYER_ITEM, ...BY_PLAYER, { $sort: { _id: 1 } }],
        openOffers: [
          { $match: { offer: { $ne: null }, trade: null } },
          { $replaceRoot: { newRoot: '$offer' } },
          { $project: { _id: 0, offerId: 1, give: 1, want: 1, ts: 1, from: '$actor' } },
          { $sort: { ts: 1, offerId: 1 } },
        ],
        settledTrades: [
          { $match: { trade: { $ne: null } } },
          {
            $project: {
              _id: 0,
              offerId: '$_id',
              from: '$offer.actor',
              to: '$trade.actor',
              gave: '$offer.give',
              got: '$offer.want',
              ts: '$trade.ts',
            },
          },
          { $sort: { ts: -1, offerId: 1 } },
          { $limit: 50 },
        ],
        winner: [{ $match: { claim: { $ne: null } } }, { $replaceRoot: { newRoot: '$claim' } }],
      },
    },
  ],
  mcp: {
    enabled: true,
    description:
      'The whole game in one read: who holds what, which offers are still open, the last ' +
      'settled trades, and the winner if anyone has claimed victory. Takes no parameters. ' +
      'This is the resource to attach in a chat to watch a game unfold — subscribe to the ' +
      'market_events collection to be told when it changes.',
    examples: [{ description: 'Read the board', action: 'execute', args: {} }],
  },
};

const holdings = {
  uri: 'holdings',
  type: 'pipeline',
  stages: [
    ...HOLDINGS,
    ...BY_PLAYER,
    // Optional: with a player, only that player. Without, everyone.
    { $ifvar: ['player', { $match: { _id: { $var: 'player' } } }] },
    { $sort: { _id: 1 } },
  ],
  mcp: {
    enabled: true,
    description:
      'What each player currently owns, derived from the ledger. Give a player name to see ' +
      'only that player; omit it to see everyone.',
    params: { player: { type: 'string', description: 'A player id, e.g. trader1' } },
    examples: [
      { description: "Everyone's holdings", action: 'execute', args: {} },
      { description: 'Just trader1', action: 'execute', args: { avars: { player: 'trader1' } } },
    ],
  },
};

const pricesFor = {
  uri: 'pricesFor',
  type: 'pipeline',
  stages: [
    { $group: { _id: '$offerId', events: { $push: '$$ROOT' } } },
    { $addFields: { offer: firstOfType('offer'), trade: firstOfType('trade') } },
    { $match: { trade: { $ne: null } } },
    { $match: { $or: [{ 'offer.give.item': { $var: 'item' } }, { 'offer.want.item': { $var: 'item' } }] } },
    {
      $project: {
        _id: 0,
        gave: '$offer.give',
        got: '$offer.want',
        from: '$offer.actor',
        to: '$trade.actor',
        ts: '$trade.ts',
      },
    },
    { $sort: { ts: -1 } },
  ],
  mcp: {
    enabled: true,
    description:
      'Every settled trade that involved a given item, newest first — the market history you ' +
      'should read before pricing your own offer.',
    params: { item: { type: 'string', enum: [...ITEMS], description: 'One of coin, grain, ore, silk' } },
    examples: [{ description: 'What has silk been going for?', action: 'execute', args: { avars: { item: 'silk' } } }],
  },
};

// ── The change stream ─────────────────────────────────────────────────────────

const newOffers = {
  uri: 'newOffers',
  stages: [{ $match: { 'fullDocument.type': 'offer' } }],
  mcp: {
    enabled: true,
    description:
      'Live feed of offers as they are published, for a client outside MCP: an agent cannot ' +
      'open it — ask how_to_call for the descriptor and hand it to a websocket client (websocat, ' +
      'a browser script). Inside MCP, subscribe to the ledger collection instead: it pushes a ' +
      'hint to re-read rather than the document itself.',
    event_type: 'Insert events whose document is an offer',
  },
};

// ── The collection's metadata, as one document ────────────────────────────────

export const LEDGER = 'market_events';

export const LEDGER_META = {
  jsonSchema: { schemaId: 'marketEvent' },
  aggrs: [board, holdings, pricesFor],
  streams: [newOffers],
  mcp: {
    enabled: true,
    description:
      'The market ledger: every event of the game, append-only. genesis = a player\'s starting ' +
      'endowment; offer = a public proposal to swap goods; trade = the acceptance that settles ' +
      'an offer; claim = a player declaring victory. Nothing here is ever updated or deleted — ' +
      'holdings, standings and the board are derived from this log by the aggregations.',
    examples: [
      {
        description: 'The offers nobody has accepted yet',
        action: 'query',
        args: { filter: { type: 'offer' }, sort: '-ts' },
      },
      {
        description:
          'Publish an offer: give 3 grain, want 2 ore. The _id must be unique — prefix it with ' +
          'your player name. Do not send actor or ts: the server sets both.',
        action: 'create',
        args: {
          body: {
            _id: 'offer:trader1:1',
            offerId: 'offer:trader1:1',
            type: 'offer',
            give: { item: 'grain', qty: 3 },
            want: { item: 'ore', qty: 2 },
          },
        },
      },
      {
        description:
          'Accept offer:trader1:1. The _id is derived from the offer id, so the FIRST ' +
          'acceptance wins and every later one gets 409 Conflict. You do not restate the terms ' +
          '— they are read from the offer — nor who you are: the server sets actor.',
        action: 'create',
        args: { body: { _id: 'accept:offer:trader1:1', offerId: 'offer:trader1:1', type: 'trade' } },
      },
      {
        description: 'Claim victory. _id is the constant "win", so only one claim can ever exist.',
        action: 'create',
        args: { body: { _id: 'win', offerId: 'win', type: 'claim' } },
      },
    ],
  },
};

/** The three starting endowments. Rotational on purpose — see the README. */
export const GENESIS = [
  { _id: 'genesis:trader1', offerId: 'genesis:trader1', type: 'genesis', actor: 'trader1',
    grants: [{ item: 'grain', qty: 12 }, { item: 'silk', qty: 4 }, { item: 'coin', qty: 60 }] },
  { _id: 'genesis:trader2', offerId: 'genesis:trader2', type: 'genesis', actor: 'trader2',
    grants: [{ item: 'ore', qty: 12 }, { item: 'grain', qty: 4 }, { item: 'coin', qty: 60 }] },
  { _id: 'genesis:trader3', offerId: 'genesis:trader3', type: 'genesis', actor: 'trader3',
    grants: [{ item: 'silk', qty: 12 }, { item: 'ore', qty: 4 }, { item: 'coin', qty: 60 }] },
];
