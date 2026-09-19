/**
 * The shape of a legal ledger event, and the cap on offer size.
 *
 * This is what keeps the game honest without a game engine: an event that is not one of the
 * five shapes is refused by the server, and no offer may move more than 3 units of a good (or
 * 30 coin) at a time — so an objective takes several trades and cannot be met in one lucky swap.
 *
 * `actor` is who wrote the event. A client that sends one has it overwritten from the
 * authenticated user (see the `mergeRequest` in acl.ts), so an event cannot be attributed to
 * somebody else.
 */
export const ITEMS = ['coin', 'grain', 'ore', 'silk'] as const;

const goods = { enum: ['grain', 'ore', 'silk'] };
const coin = { enum: ['coin'] };

/** A side of an offer: at most 3 of a good, or 30 coin. */
const terms = {
  type: 'object',
  required: ['item', 'qty'],
  oneOf: [
    { properties: { item: goods, qty: { type: 'integer', minimum: 1, maximum: 3 } } },
    { properties: { item: coin, qty: { type: 'integer', minimum: 1, maximum: 30 } } },
  ],
};

export const MARKET_EVENT_SCHEMA = {
  $schema: 'http://json-schema.org/draft-04/schema#',
  description:
    'Shape of a market ledger event. actor is who wrote it, set by the server from the ' +
    'authenticated user. An event that is not one of the five legal shapes is refused, and no ' +
    'offer may move more than 3 units of a good (or 30 coin) at a time.',
  type: 'object',
  oneOf: [
    {
      title: 'genesis',
      required: ['_id', 'offerId', 'type', 'actor', 'grants'],
      properties: {
        type: { enum: ['genesis'] },
        actor: { type: 'string' },
        grants: {
          type: 'array',
          minItems: 1,
          items: {
            type: 'object',
            required: ['item', 'qty'],
            properties: { item: { enum: [...ITEMS] }, qty: { type: 'integer', minimum: 1 } },
          },
        },
      },
    },
    {
      title: 'offer',
      required: ['_id', 'offerId', 'type', 'actor', 'give', 'want'],
      properties: { type: { enum: ['offer'] }, actor: { type: 'string' }, give: terms, want: terms },
    },
    {
      title: 'trade',
      required: ['_id', 'offerId', 'type', 'actor'],
      properties: { type: { enum: ['trade'] }, actor: { type: 'string' } },
    },
    {
      title: 'cancel',
      required: ['_id', 'offerId', 'type', 'actor'],
      properties: { type: { enum: ['cancel'] }, actor: { type: 'string' } },
    },
    {
      title: 'claim',
      required: ['_id', 'offerId', 'type', 'actor'],
      properties: { type: { enum: ['claim'] }, actor: { type: 'string' } },
    },
  ],
};
