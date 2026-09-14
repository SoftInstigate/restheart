/**
 * The market as a graph: players → the offers they published → the player who made each.
 *
 * Over REST that would be one read per player; here it is one round trip. `me` resolves to
 * whoever is authenticated, server-side.
 *
 * The mappings name the database, and on RESTHeart Cloud that is the service's own — its id —
 * which is why this is a function of `srvId` rather than a constant.
 */
import { LEDGER } from './ledger.ts';
import { ITEMS_COLL, PLAYERS_COLL } from './reference.ts';

export const GRAPHQL_APP = 'market';

export const graphqlApp = (db: string) => ({
  descriptor: { name: GRAPHQL_APP, description: 'The market game, as a graph.', enabled: true, uri: GRAPHQL_APP },
  schema:
    'type Item { _id: String name: String note: String } ' +
    'type Terms { item: String qty: Int } ' +
    'type Offer { offerId: String from: String give: Terms want: Terms giver: Player } ' +
    'type Player { _id: String name: String motto: String offers: [Offer] } ' +
    'type Query { players(limit: Int = 10): [Player] offers(limit: Int = 20): [Offer] items: [Item] me: Player }',
  mappings: {
    Query: {
      players: { db, collection: PLAYERS_COLL, find: {}, sort: { _id: 1 }, limit: { $arg: 'limit' } },
      offers: { db, collection: LEDGER, find: { type: 'offer' }, sort: { ts: -1 }, limit: { $arg: 'limit' } },
      items: { db, collection: ITEMS_COLL, find: {}, sort: { _id: 1 } },
      me: { db, collection: PLAYERS_COLL, stages: [{ $match: { _id: { $arg: '@user._id' } } }] },
    },
    Player: {
      offers: { db, collection: LEDGER, find: { type: 'offer', actor: { $fk: '_id' } }, sort: { ts: -1 }, limit: 20 },
    },
    Offer: {
      // The document says `actor`; the graph says `from`. A field mapping renames it.
      from: 'actor',
      giver: { db, collection: PLAYERS_COLL, find: { _id: { $fk: 'actor' } } },
    },
  },
  mcp: {
    enabled: true,
    description:
      'The market game as a graph: players, the offers they published, and the item catalogue ' +
      '— in one round trip. Use this when you want a player together with their offers; over ' +
      'REST that would be one read per player. `me` resolves to whoever you are authenticated as.',
    examples: [
      {
        description: 'Every player with the offers they have published',
        args: { body: { query: '{ players { _id name motto offers { offerId give { item qty } want { item qty } } } }' } },
      },
      {
        description: 'Who am I, and what have I offered?',
        args: { body: { query: '{ me { _id name offers { offerId give { item qty } want { item qty } } } }' } },
      },
      {
        description: 'Introspect the schema to see every field you can ask for',
        args: { body: { query: '{ __schema { queryType { fields { name description } } } }' } },
      },
    ],
  },
});
