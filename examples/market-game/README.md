# The market game

An application with no code. Three AI agents trade goods with each other through a RESTHeart
Cloud service's MCP server. Each has a secret objective and the wrong pile of goods. You watch
the board from Claude.

Nothing here is a program. It is a data model: one collection, a schema, six rules, four
aggregations, three users and five permissions. RESTHeart Cloud enforces it and publishes it
over MCP, and the agents are the user interface. That is the point of the example: **model the
domain, and the application is there.**

## Play it

You need a RESTHeart Cloud service, Node 22.18 or later, and Claude. A free service is fine, but make it a **fresh** one: a service set up for another app carries that app's Guards rules and user schema — a consents gate, for one — and they apply to the traders too.

### 1. Set up the service

```bash
npm install
npx rhc login                       # paste a personal access token from your console profile

export TRADER1_PASSWORD='…' TRADER2_PASSWORD='…' TRADER3_PASSWORD='…'
npx rhc setup --srv <srvId>         # the six characters at the start of your service URL
```

This creates the ledger collection, its rules, four aggregations, a change stream, a GraphQL app,
three trader accounts and their permissions. Pick long passwords: the service checks their
strength. Wait about 20 seconds after the first run for the permissions to take effect.

Run it again any time: it changes only what differs. To wipe the ledger and start a new game:

```bash
npm run reset
```

Then check that the rules refuse what they must, as the three traders:

```bash
export MCP_BASE=https://<your service URL>
./agents/smoke.sh
```

It plays the moves that must go through, then an offer over the cap, an over-commitment, a
self-deal, a trade on a missing offer, a false claim of victory and a second acceptance, and
checks each answer. It appends to the ledger, so `npm run reset` afterwards for a clean board.

### 2. Watch from Claude

1. **Settings → Connectors → Add custom connector**. Paste the MCP endpoint from the console's
   **MCP Server** page.
2. Sign in as `trader1` when Claude asks.
3. Attach the resource ending in `/market_events/_aggrs/board`. That one read is the whole game.
4. Subscribe to the resource ending in `/market_events`. Claude is told when the ledger
   changes, and re-reads the board.

### 3. Start the agents

Give each agent [`agents/trader.md`](agents/trader.md) with `{{PLAYER}}`, `{{PASSWORD}}` and
`{{BASE_URL}}` filled in. The base URL is your service URL, from the console's **Connect** page.

Each agent needs its own MCP session, because each is a different user of the service.
[`agents/mcp.sh`](agents/mcp.sh) opens one per user:

```bash
export MCP_BASE=https://<your service URL>
./agents/mcp.sh trader1 "$TRADER1_PASSWORD" tools/call '{"name":"list_apis","arguments":{}}'
```

The agents discover the API, make offers, accept each other's, and one of them eventually claims
victory. The board in Claude changes as they go. Nobody told them how the game works: they read
it from the catalogue, the rules and the errors they get back.

## How it works

Everything below is configuration in `rhc.setup.ts` and the `game/` folder. There is no server,
no game loop and no client: the agents read the catalogue, understand the rules from the
descriptions and the error messages, and play.

### One collection, no state

`market_events` is an append-only ledger. It is the only thing anybody writes to.

| Event | Means |
|---|---|
| `genesis` | what a player starts with |
| `offer` | "I give X for Y" |
| `trade` | somebody accepted an offer |
| `claim` | "I won" |

Who owns what, which offers are open, who is winning: all of it is computed from the ledger by
aggregations. Nothing is ever updated or deleted.

**First acceptance wins.** A trade's `_id` is derived from the offer's id. MongoDB refuses a
duplicate `_id`, so the second player to accept the same offer gets `409`. Same for victory: a
claim has the fixed `_id` `win`, so only one can exist.

### The rules

Six constraints on the collection, each an aggregation that looks for trouble. They run inside
the write's transaction, for every writer. A write that breaks one is refused and rolled back.

| Rule | Refuses a write that would leave… |
|---|---|
| `noNegativeHoldings` | someone owning less than nothing |
| `noOverCommitment` | someone with open offers for more than they hold |
| `tradeSettlesAnOffer` | a trade that accepts no real offer |
| `noSelfDealing` | a player accepting their own offer |
| `claimIsEarned` | a victory claim the claimant's goods do not justify |
| `gameEndsAtTheClaim` | any offer or trade after somebody has won |

A JSON Schema does the per-document checks, and caps an offer at 3 units of a good or 30 coin,
so winning takes several trades.

### Who sees what

Each agent is a real user of the service: `trader1`, `trader2`, `trader3`, role `trader`.

- `market_objectives` has a read filter on the signed-in user. Everyone reads the same URL and
  gets only their own objective.
- On every ledger write the server sets `actor` to the signed-in user and `ts` to now. A player
  cannot post in someone else's name or backdate an event.
- Traders can `POST /market_events` and read. Nothing else.

### The three 409s

| Body contains | Meaning | Do |
|---|---|---|
| `"retryable": true` | two writes collided, yours was not applied | send it again |
| `"constraint": "…"`, `"violations": […]` | a rule refused it | read the board, rethink |
| neither | that `_id` exists: someone accepted first | move on |

### What is exposed over MCP

| Resource | What it is |
|---|---|
| `market_events` | the ledger. Subscribe to this |
| `market_events/_aggrs/board` | holdings, open offers, trades and the winner in one read |
| `market_events/_aggrs/holdings` | who owns what, optionally for one `player` |
| `market_events/_aggrs/pricesFor` | trade history for one `item` |
| `market_events/_aggrs/archive` | listed but refused: it uses `$merge`, which is blacklisted |
| `market_events/_streams/newOffers` | a websocket pushing each new offer |
| `market_items`, `market_players` | the four goods and the three players |
| `market_objectives` | your objective, and only yours |
| `graphql/market` | players and their offers in one round trip |

### Why the game does not stall

Every good is held by two players, one with 12 and one with 4, and each objective asks for the
good the other two hold. Nobody can sit still. Everyone starts with 60 coin and needs 70, and
there are 180 coin for three thresholds of 70, so at most two players can hold enough at once:
something always has to be sold.

## Files

| File | What it is |
|---|---|
| `rhc.setup.ts` | what the service must have, as steps that check and apply |
| `game/schema.ts` | the JSON Schema for ledger events |
| `game/ledger.ts` | the holdings derivation, the aggregations, the change stream, the endowments |
| `game/rules.ts` | the six constraints |
| `game/reference.ts` | items, players, objectives |
| `game/acl.ts` | the five permissions |
| `game/graphql.ts` | the GraphQL app |
| `agents/trader.md` | the agent's prompt |
| `agents/mcp.sh` | a per-user MCP client |
| `agents/smoke.sh` | every rule exercised as the traders, each answer checked |

Everything is in the console afterwards: the rules on the **Constraints** page, where **Run**
tries each against the data; the resources on the **MCP Server** page, under **What an agent
sees**.

More in the RESTHeart Cloud manual: [MCP Server](https://restheart.org/docs/cloud/mcp), [Data
Constraints](https://restheart.org/docs/cloud/constraints), [the `rhc`
CLI](https://restheart.org/docs/cloud/cli).

## Things that bit us

Each of these broke the example once.

1. **`path-prefix` matches whole path segments.** `path-prefix('/market_')` matches nothing.
   Name each collection.
2. **In aggregation expressions, `$eq: ["$field", null]` is false when the field is missing.**
   Test with `{"$eq": [{"$type": "$field"}, "object"]}` instead.
3. **A bulk delete needs a filter.** To delete everything: `filter={"_id":{"$exists":true}}`.
4. **Between two matching permissions the higher priority wins.** Do not rely on that for a
   read filter: give the filtered collection its own permission and exclude it from the others.
5. **Subscribing to an aggregation never notifies.** Only collections have change streams.
   Subscribe to the collection, read the aggregation.
6. **"Does it exist?" is not "is it what I meant?"** The setup compares each stored document
   with the intended one, so an edited rule or permission is re-applied.
