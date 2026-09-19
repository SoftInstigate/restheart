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
import { OBJECTIVES_SEED, SECRETS } from './reference.ts';

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

/**
 * A time as an ISO-8601 string rather than `{"$date": 1789803391000}`.
 *
 * The wrapped form is RESTHeart's faithful rendering of a BSON date and there is nothing wrong
 * with it, but every reader of this game — an agent, a browser, a person — wants to read the
 * time, not to unwrap it first. The ledger keeps real dates; only what is derived is flattened.
 */
const iso = (field: string) => ({ $dateToString: { date: field } });

export const GROUPED_BY_OFFER = [
  { $group: { _id: '$offerId', events: { $push: '$$ROOT' } } },
  {
    $addFields: {
      genesis: firstOfType('genesis'),
      offer: firstOfType('offer'),
      trade: firstOfType('trade'),
      cancel: firstOfType('cancel'),
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
      // An offer holds its goods until it settles — or until its author withdraws it, which is
      // what `cancel` is for: without it a mistaken offer commits the goods for the rest of the
      // game, and the market fills with offers nobody will ever cross.
      commitments: {
        $cond: [
          {
            $and: [
              isObject('$offer'),
              { $ne: [{ $type: '$trade' }, 'object'] },
              { $ne: [{ $type: '$cancel' }, 'object'] },
            ],
          },
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

/**
 * A settled trade where coin met a good, with the price of one unit.
 *
 * Only half the trades have a price: a swap of grain for silk says nothing about what either is
 * worth. Those are dropped here rather than handed over as a pair of quantities for the reader to
 * divide, which is what they were doing by eye.
 */
export const PRICED_TRADES = [
  { $match: { trade: { $ne: null } } },
  {
    $addFields: {
      coinQty: {
        $cond: [{ $eq: ['$offer.give.item', 'coin'] }, '$offer.give.qty',
          { $cond: [{ $eq: ['$offer.want.item', 'coin'] }, '$offer.want.qty', null] }],
      },
      goodItem: {
        $cond: [{ $eq: ['$offer.give.item', 'coin'] }, '$offer.want.item',
          { $cond: [{ $eq: ['$offer.want.item', 'coin'] }, '$offer.give.item', null] }],
      },
      goodQty: {
        $cond: [{ $eq: ['$offer.give.item', 'coin'] }, '$offer.want.qty',
          { $cond: [{ $eq: ['$offer.want.item', 'coin'] }, '$offer.give.qty', null] }],
      },
    },
  },
  { $match: { goodItem: { $ne: null }, goodQty: { $gt: 0 } } },
  { $addFields: { unitPrice: { $round: [{ $divide: ['$coinQty', '$goodQty'] }, 2] } } },
];

/** Those trades folded into one row per good: how many, the latest price, the average. */
export const PRICE_SUMMARY = [
  { $sort: { 'trade.ts': -1 } },
  {
    $group: {
      _id: '$goodItem',
      trades: { $sum: 1 },
      lastPrice: { $first: '$unitPrice' },
      avgPrice: { $avg: '$unitPrice' },
    },
  },
  { $project: { _id: 0, item: '$_id', trades: 1, lastPrice: 1, avgPrice: { $round: ['$avgPrice', 2] } } },
  { $sort: { item: 1 } },
];

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
          { $match: { offer: { $ne: null }, trade: null, cancel: null } },
          { $replaceRoot: { newRoot: '$offer' } },
          { $sort: { ts: 1, offerId: 1 } },
          { $project: { _id: 0, offerId: 1, give: 1, want: 1, from: '$actor', ts: iso('$ts') } },
        ],
        settledTrades: [
          { $match: { trade: { $ne: null } } },
          { $sort: { 'trade.ts': -1, _id: 1 } },
          { $limit: 50 },
          {
            $project: {
              _id: 0,
              offerId: '$_id',
              from: '$offer.actor',
              to: '$trade.actor',
              gave: '$offer.give',
              got: '$offer.want',
              ts: iso('$trade.ts'),
            },
          },
        ],
        // The number to use next, so nobody has to scan the ledger for the ids they have burned.
        // A player who has published nothing is absent: their first offer is `offer:<player>:1`.
        nextOfferId: [
          { $match: { offer: { $ne: null } } },
          {
            $project: {
              player: '$offer.actor',
              n: { $convert: { input: { $last: { $split: ['$_id', ':'] } }, to: 'int', onError: 0, onNull: 0 } },
            },
          },
          { $group: { _id: '$player', last: { $max: '$n' } } },
          {
            $project: {
              _id: 0,
              player: '$_id',
              nextOfferId: { $concat: ['offer:', '$_id', ':', { $toString: { $add: ['$last', 1] } }] },
            },
          },
          { $sort: { player: 1 } },
        ],
        // What a unit has actually gone for, per good: the last price and the average of them all.
        prices: [...PRICED_TRADES, ...PRICE_SUMMARY],
        winner: [
          { $match: { claim: { $ne: null } } },
          { $replaceRoot: { newRoot: '$claim' } },
          { $addFields: { ts: iso('$ts') } },
        ],
      },
    },
    // An empty `winner` does not say the game is open. It says nobody had won when this was read,
    // and a reader has no way to tell how long ago that was: one player read it seven seconds
    // before a claim landed and spent its turn on a move the rules had already closed.
    {
      $addFields: {
        over: { $gt: [{ $size: '$winner' }, 0] },
        asOf: iso('$$NOW'),
      },
    },
  ],
  mcp: {
    enabled: true,
    description:
      'The whole game in one read: who holds what, which offers are still open, the last ' +
      'settled trades, what things have sold for, the next free offer id of each player, and ' +
      'the winner if anyone has claimed victory. `over` says the game is finished and `asOf` ' +
      'says when this was read, because an empty winner only means nobody had won by then. In ' +
      'settledTrades the point of view is the player who published the offer: `from` is that ' +
      'player, `gave` is what they handed over and `got` is what they received. Takes no ' +
      'parameters. This is the resource to attach in a chat to watch a game unfold — subscribe ' +
      'to the market_events collection to be told when it changes.',
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
      { description: 'Just trader1', action: 'execute', args: { player: 'trader1' } },
    ],
  },
};

const pricesFor = {
  uri: 'pricesFor',
  type: 'pipeline',
  stages: [
    { $group: { _id: '$offerId', events: { $push: '$$ROOT' } } },
    { $addFields: { offer: firstOfType('offer'), trade: firstOfType('trade') } },
    { $match: { $or: [{ 'offer.give.item': { $var: 'item' } }, { 'offer.want.item': { $var: 'item' } }] } },
    ...PRICED_TRADES,
    { $sort: { 'trade.ts': -1 } },
    {
      $project: {
        _id: 0,
        item: '$goodItem',
        qty: '$goodQty',
        coin: '$coinQty',
        unitPrice: 1,
        gave: '$offer.give',
        got: '$offer.want',
        from: '$offer.actor',
        to: '$trade.actor',
        ts: iso('$trade.ts'),
      },
    },
  ],
  mcp: {
    enabled: true,
    description:
      'What a given item has sold for, newest first, with the price of one unit. Only trades ' +
      'where coin met a good are here: a swap of grain for silk prices neither. The board ' +
      'carries the same thing summarised, so read this when you want the individual deals.',
    params: { item: { type: 'string', enum: [...ITEMS], description: 'One of coin, grain, ore, silk' } },
    examples: [{ description: 'What has silk been going for?', action: 'execute', args: { item: 'silk' } }],
  },
};

/**
 * Your own position, and whether you have won: holdings, the objective that only you can unlock,
 * how far each requirement still is, and `canClaim`.
 *
 * <p>Deliberately without the objective's `hint`. The hint is prose written for the opening
 * position and it does not age: read beside holdings that have moved, it contradicts them, and an
 * agent that believes it plays the wrong move. What is true at any moment is in `progress`, which
 * is computed — `short` and `met`, requirement by requirement.
 *
 * The secret is checked inside the pipeline, not by a permission: the objective is picked by a
 * branch that matches the player AND the secret, so a wrong pair matches nothing and the
 * aggregation returns nothing. That leaves the permission argument-free, which is what keeps this
 * resource in the MCP catalogue at all — a catalogue is built by asking the ACL what a caller may
 * read, and that question carries no arguments.
 *
 * The objectives are written into the pipeline rather than read from their collection, because
 * `$lookup` is refused: RESTHeart blacklists it in aggregations. The same table already appears in
 * the claimIsEarned rule, built from the same seed, so the two cannot drift.
 */
/** player + secret → that player's objective, as pipeline branches. See the note on myState. */
const OBJECTIVE_TABLE = OBJECTIVES_SEED.map(o => ({
  case: { $and: [{ $eq: ['$_id', o.player] }, { $eq: [{ $var: 'secret' }, SECRETS[o.player]] }] },
  then: { goal: o.goal, requires: o.requires },
}));

const myState = {
  uri: 'myState',
  type: 'pipeline',
  stages: [
    ...HOLDINGS,
    { $match: { '_id.player': { $var: 'trader' } } },
    ...BY_PLAYER,
    { $addFields: { objective: { $switch: { branches: OBJECTIVE_TABLE, default: null } } } },
    { $match: { objective: { $ne: null } } },
    {
      $addFields: {
        progress: {
          $map: {
            input: '$objective.requires',
            as: 'r',
            in: {
              item: '$$r.item',
              need: '$$r.qty',
              have: {
                $let: {
                  vars: { g: { $first: { $filter: { input: '$goods', as: 'g', cond: { $eq: ['$$g.item', '$$r.item'] } } } } },
                  in: { $ifNull: ['$$g.qty', 0] },
                },
              },
            },
          },
        },
      },
    },
    {
      $addFields: {
        progress: {
          $map: {
            input: '$progress',
            as: 'p',
            in: {
              item: '$$p.item',
              need: '$$p.need',
              have: '$$p.have',
              short: { $max: [0, { $subtract: ['$$p.need', '$$p.have'] }] },
              met: { $gte: ['$$p.have', '$$p.need'] },
            },
          },
        },
      },
    },
    { $addFields: { canClaim: { $allElementsTrue: { $map: { input: '$progress', as: 'p', in: '$$p.met' } } } } },
    { $project: { _id: 0, player: '$_id', goal: '$objective.goal', goods: 1, progress: 1, canClaim: 1 } },
  ],
  mcp: {
    enabled: true,
    description:
      'Where you stand: what you hold, your objective, how much of each requirement is still ' +
      'missing, and canClaim — true when the board already says you have won. Read it after ' +
      'your moves as well as before them, or you will win a round before you notice. Send the ' +
      'same trader and secret you sign a write with; a wrong pair returns nothing. The objective\'s opening hint ' +
      'is not here on purpose: it was written for the first move and says nothing true later.',
    params: {
      trader: { type: 'string', description: 'Your player id, e.g. trader1' },
      secret: { type: 'string', description: 'The secret that proves you are that player' },
    },
    examples: [{
      description: 'Where am I, and can I claim?',
      action: 'execute',
      args: { trader: 'trader1', secret: '<your secret>' },
    }],
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
  aggrs: [board, holdings, pricesFor, myState],
  streams: [newOffers],
  mcp: {
    enabled: true,
    description:
      'The market ledger: every event of the game, append-only. genesis = a player\'s starting ' +
      'endowment; offer = a public proposal to swap goods; trade = the acceptance that settles ' +
      'an offer; cancel = the author withdrawing an offer nobody took, which frees the goods it ' +
      'held; claim = a player declaring victory. Nothing here is ever updated or deleted — ' +
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
        description:
          'Withdraw your own offer:trader1:1, which nobody has accepted. The goods it was ' +
          'holding become available again. Only the player who published it may withdraw it, ' +
          'and an offer already accepted cannot be withdrawn.',
        action: 'create',
        args: { body: { _id: 'cancel:offer:trader1:1', offerId: 'offer:trader1:1', type: 'cancel' } },
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
