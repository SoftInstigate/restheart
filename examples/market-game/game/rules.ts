/**
 * The rules of the game, as constraints on the ledger.
 *
 * The JSON Schema in schema.ts judges one document at a time, and the unique `_id` makes
 * "first acceptance wins" atomic. Neither can see a property of the *collection*: that a
 * player is promising more than it holds, that a claim of victory is true, that the game is
 * over. These can, because a constraint's pipeline runs over the whole collection inside the
 * write's transaction, and a write that makes it return anything is refused and rolled back —
 * for every writer, admin included. The documents it returns come back as `violations`.
 *
 * All of them are built on the one holdings derivation in ledger.ts. Each is a query for
 * trouble: the rule holds while the query finds nothing.
 */
import { GROUPED_BY_OFFER, HOLDINGS } from './ledger.ts';
import { OBJECTIVES_SEED } from './reference.ts';

/**
 * Nobody may end up owning less than nothing.
 *
 * Two offers of 3 silk while holding 4, both accepted, and the ledger would faithfully record
 * two trades that should never have settled. This refuses the second acceptance.
 */
const noNegativeHoldings = {
  name: 'noNegativeHoldings',
  message: 'a player cannot hold a negative quantity of any good',
  stages: [...HOLDINGS, { $match: { qty: { $lt: 0 } } }],
};

/**
 * Nobody may promise more than they hold.
 *
 * The stricter rule, one step earlier: `available` is what a player owns minus what their
 * open offers have committed, and this refuses the second *offer* rather than the second
 * acceptance. It changes how agents must play — read your own `available` before publishing,
 * and one open offer at a time per good is the safe habit.
 */
const noOverCommitment = {
  name: 'noOverCommitment',
  message: 'a player cannot have open offers for more than it holds',
  stages: [...HOLDINGS, { $match: { available: { $lt: 0 } } }],
};

/**
 * A trade settles an offer that exists. An acceptance whose `offerId` names no offer moves
 * nothing, but it would occupy the `_id` that the real acceptance needs.
 */
const tradeSettlesAnOffer = {
  name: 'tradeSettlesAnOffer',
  message: 'a trade must accept an offer that exists',
  stages: [
    ...GROUPED_BY_OFFER,
    { $match: { $expr: { $and: [{ $eq: [{ $type: '$trade' }, 'object'] }, { $ne: [{ $type: '$offer' }, 'object'] }] } } },
    { $project: { _id: 1, trade: '$trade.actor' } },
  ],
};

/**
 * You cannot accept your own offer. Economically it nets to zero, so nothing would break;
 * but it closes an offer nobody else could then take, and the board would show a trade
 * that never happened.
 */
const noSelfDealing = {
  name: 'noSelfDealing',
  message: 'a player cannot accept their own offer',
  stages: [
    ...GROUPED_BY_OFFER,
    { $match: { $expr: { $and: [{ $eq: [{ $type: '$trade' }, 'object'] }, { $eq: ['$trade.actor', '$offer.actor'] }] } } },
    { $project: { _id: 1, player: '$offer.actor' } },
  ],
};

/**
 * A victory is claimed only when the board proves it.
 *
 * The objectives are the ones in reference.ts, inlined here as a table: a constraint cannot
 * `$lookup` (it is blacklisted, and the objectives are private anyway), and taking them from
 * the same file that seeds them is what keeps the rule and the game from disagreeing.
 *
 * For each claim, the claimant's requirements are looked up in that table and each is checked
 * against the claimant's holdings. A requirement nothing satisfies is `unmet`, and a claim with
 * any unmet requirement is a violation. A claimant the table does not know gets an
 * unsatisfiable requirement, so an unknown name can never win either.
 */
const objectiveTable = OBJECTIVES_SEED.map(o => ({
  case: { $eq: ['$claim.actor', o.player] },
  then: o.requires,
}));

const claimIsEarned = {
  name: 'claimIsEarned',
  message: 'a player may claim victory only when their holdings meet their objective',
  stages: [
    {
      $facet: {
        holdings: HOLDINGS,
        claims: [{ $match: { type: 'claim' } }, { $project: { _id: 0, actor: 1 } }],
      },
    },
    // One document per claim; with no claim there is nothing here, and the rule holds.
    { $unwind: '$claims' },
    { $project: { holdings: 1, claim: '$claims' } },
    {
      $addFields: {
        requires: { $switch: { branches: objectiveTable, default: [{ item: 'nobody-holds-this', qty: 1 }] } },
      },
    },
    {
      $addFields: {
        unmet: {
          $filter: {
            input: '$requires',
            as: 'r',
            cond: {
              $not: [
                {
                  $anyElementTrue: {
                    $map: {
                      input: '$holdings',
                      as: 'h',
                      in: {
                        $and: [
                          { $eq: ['$$h._id.player', '$claim.actor'] },
                          { $eq: ['$$h._id.item', '$$r.item'] },
                          { $gte: ['$$h.qty', '$$r.qty'] },
                        ],
                      },
                    },
                  },
                },
              ],
            },
          },
        },
      },
    },
    { $match: { $expr: { $gt: [{ $size: '$unmet' }, 0] } } },
    { $project: { _id: 0, player: '$claim.actor', unmet: 1 } },
  ],
};

/**
 * The game ends at the claim. Once somebody has won, no further offer or trade may be
 * appended — the server stamps `ts`, so "after" cannot be forged.
 */
const gameEndsAtTheClaim = {
  name: 'gameEndsAtTheClaim',
  message: 'the game is over: no offers or trades after a victory has been claimed',
  stages: [
    {
      $facet: {
        won: [{ $match: { type: 'claim' } }, { $project: { _id: 0, ts: 1 } }],
        moves: [{ $match: { type: { $in: ['offer', 'trade'] } } }, { $project: { _id: 1, type: 1, actor: 1, ts: 1 } }],
      },
    },
    { $unwind: '$won' },
    { $unwind: '$moves' },
    { $match: { $expr: { $gt: ['$moves.ts', '$won.ts'] } } },
    { $replaceRoot: { newRoot: '$moves' } },
  ],
};

export const CONSTRAINTS = [
  noNegativeHoldings,
  noOverCommitment,
  tradeSettlesAnOffer,
  noSelfDealing,
  claimIsEarned,
  gameEndsAtTheClaim,
];
