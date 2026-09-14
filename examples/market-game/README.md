# The market game — a domain agents play, over MCP, on RESTHeart Cloud

Three agents, each with a private objective and a deliberately wrong pile of goods, trading
with each other through a RESTHeart Cloud service's MCP server while a human watches the board
from a chat.

It exists to exercise the whole surface against something that has *stakes*: a collection,
four aggregations, a change stream and a GraphQL app, read by three authenticated identities
whose permissions genuinely differ; six rules the collection enforces on every write; and a
board you can attach to a conversation and subscribe to. All of it configured by one file,
`rhc.setup.ts`, that you can read, edit and re-run.

See [MCP Server](https://restheart.org/docs/cloud/mcp), [Data
Constraints](https://restheart.org/docs/cloud/constraints) and [the `rhc`
CLI](https://restheart.org/docs/cloud/cli) in the RESTHeart Cloud manual.

## The idea: derive the state, never mutate it

There is no game engine. There are no balances, no turn order, no lock.

`market_events` is an **append-only ledger** and it is the only thing anybody writes to.
Everything you would expect to be stored — who owns what, which offers are open, who is
winning — is an aggregation over that log:

| Event | Means |
|---|---|
| `genesis` | a player's starting endowment |
| `offer` | a public proposal to swap goods |
| `trade` | the acceptance that settles an offer |
| `claim` | a player declaring victory |

An offer is settled by appending a `trade` whose `_id` is derived from the offer's id
(`accept:offer:trader1:1`). MongoDB's unique `_id` does the rest: **the first acceptance
wins and every later one gets 409 Conflict.** No turns, no locking, no race. The same trick
picks the winner — a victory claim has the constant `_id` of `"win"`, so only one can exist.

The accepting player does not restate the terms. They are read back from the offer, which is
in the same collection, so they cannot be altered on the way in.

## The rules, and who enforces them

Three mechanisms, each doing what the others cannot.

**The JSON Schema** (`game/schema.ts`) judges one document at a time. It refuses anything that
is not one of the four shapes, and caps an offer at **3 units of a good, or 30 coin**. That cap
is the difference between a game and a coin flip: an objective needs five or six trades, so the
market has to actually run for a while.

**The unique `_id`** makes "first acceptance wins" atomic, with no code.

**The constraints** (`game/rules.ts`) see what neither of those can: properties of the
*collection*. Each is an aggregation pipeline that searches for trouble; it runs inside the
write's transaction, and a write that makes it return anything is refused and rolled back — for
every writer, `admin` included. The documents it returns come back as `violations`.

| Rule | Refuses a write that would leave… |
|---|---|
| `noNegativeHoldings` | anyone owning less than nothing |
| `noOverCommitment` | anyone with open offers for more than they hold |
| `tradeSettlesAnOffer` | a trade whose `offerId` names no offer |
| `noSelfDealing` | a player accepting their own offer |
| `claimIsEarned` | a victory claim the claimant's holdings do not justify |
| `gameEndsAtTheClaim` | an offer or trade after somebody has won |

All six are built on the one holdings derivation in `game/ledger.ts`, which the `board` and
`holdings` aggregations also use — one copy, so an aggregation and the rule that guards it
cannot disagree. `claimIsEarned` goes one further: it takes the objectives from `game/reference.ts`,
the same file that seeds them, so the rule and the game cannot drift apart either. A constraint
cannot `$lookup` — it is blacklisted, and the objectives are private anyway — so they are inlined
as a `$switch` table.

Two consequences worth knowing. Constraints need a **replica set**, which RESTHeart Cloud gives
you. And writes to a constrained collection **serialize**: every one contends a guard document,
which is what stops two trades that are each valid on their own snapshot and invalid together
from both committing. For three agents that costs nothing.

### Two 409s, and what a client does with each

A write can be refused with `409 Conflict` for two reasons that call for opposite responses,
and the body says which:

```json
{ "retryable": true, "message": "..." }
```

is a **write conflict**: two writes to the ledger collided and this one lost *without being
applied*. Send the same request again. Nothing else sets `retryable`, so the whole check is
`if (error.retryable)`.

```json
{ "constraint": "noOverCommitment",
  "message": "a player cannot have open offers for more than it holds",
  "violations": [ { "_id": { "player": "trader1", "item": "silk" }, "qty": 4, "committed": 6, "available": -2 } ] }
```

is a **violated rule**. Retrying repeats the answer; read the board instead. A `409` with
neither is the third case — the `_id` already exists, which in this game means somebody
accepted that offer first.

## What is exposed

| Resource | Kind | What it is |
|---|---|---|
| `_schemas/marketEvent` | JSON Schema | the shape of a legal event, and the cap on offer size |
| `market_events` | collection | the ledger — **subscribe to this** |
| `market_events/_aggrs/board` | aggregation | a `$facet` giving holdings, open offers, settled trades and the winner — **read this** |
| `market_events/_aggrs/holdings` | aggregation | who owns what; optional `player` parameter |
| `market_events/_aggrs/pricesFor` | aggregation | trade history for one item; **required** `item` parameter |
| `market_events/_aggrs/archive` | aggregation | described in the catalogue but refused at call time: it uses `$merge`, which is blacklisted |
| `market_events/_streams/newOffers` | change stream | a websocket that pushes new offers |
| `market_items` | collection | the four tradable things: coin, grain, ore, silk |
| `market_players` | collection | the three players |
| `market_objectives` | collection | your objective, and **only** yours |
| `graphql/market` | GraphQL app | players → their offers → the player who made them, in one round trip |

The holdings are derived by projecting each settled trade into four movements (−qty to the
giver, +qty to the taker, twice) and each genesis into one per grant, then `$unwind` and
`$group`. No `$lookup` anywhere — it is blacklisted, and everything lives in one collection
precisely so that never comes up.

## Identity is the game mechanic

Each agent is a real user of the service (`trader1`, `trader2`, `trader3`, role `trader`).

`market_objectives` carries a `readFilter` on the authenticated user. Every player issues the
same read and gets a different single document; the filter is resolved server-side and a
caller cannot supply it. It holds identically over REST and through MCP.

The filter names "me" twice — `{ "$or": [{ "player": "@user._id" }, { "player": "@user.sub" }] }`
— because a trader can arrive two ways: signed in with a password, where the account is the user
document and its id is `_id`; or with a token from the service's own sign-in page, where the
account is the token's claims and the id is `sub`. A variable that does not resolve matches
nothing, so the branch that does not apply is simply empty, and neither can ever name somebody else.

Writing is `POST /market_events` and nothing else. No PATCH, no DELETE, no other collection.
There is no mutable state to corrupt.

Two fields are not the client's to set. The permission carries

```json
"mongo": { "mergeRequest": { "actor": "@user._id", "ts": "@now" } }
```

so the server overwrites `actor` with whoever authenticated and stamps `ts` itself. Before this a
player could publish an offer in someone else's name, or claim victory on their behalf. And since
the board orders events by `ts`, a client-supplied time let a player rewrite the order of history.
No code: an ACL rule.

## Set it up

You need a RESTHeart Cloud service — a free one will do — and Node 22.18 or later.

```bash
npm install                      # the CLI the setup file imports
npx rhc login                    # asks for a personal access token, from your profile in the console

export TRADER1_PASSWORD='…' TRADER2_PASSWORD='…' TRADER3_PASSWORD='…'
npx rhc setup --srv <srvId>      # the six characters at the start of your service URL
```

The passwords are read only the first time, when the accounts are created; the service checks
their strength with the same rule it applies to sign-up, so pick long ones. After that a re-run
needs nothing in the environment.

Every step is a check and an apply. Run it again and it writes nothing and reports each step
satisfied; edit an objective, a permission or a rule and run it again and only that reaches the
service. `--dry-run` says what is missing without changing anything.

```bash
npx rhc setup --srv <srvId> --dry-run
```

The one thing a re-run never touches is the game in progress. To wipe the ledger and start over:

```bash
npx rhc setup --srv <srvId> --force game
```

Everything the file configures is visible in the console afterwards — the rules on the
**Constraints** page, where **Run** tries each against the data; the published resources on the
**MCP Server** page, where **What an agent sees** shows the catalogue as any trader would get it.

### One cache stands between you and a working game

The **ACL cache**: a new permission takes up to 20 seconds to apply, and until it does the
traders get 403 for no visible reason. The MCP catalogue, by contrast, refreshes the moment the
metadata changes.

## Watch a game from Claude

1. In Claude, **Settings → Connectors → Add custom connector**, and paste the endpoint from the
   console's **MCP Server** page: `https://<srvId>.<region>.restheart.com/mcp`.
2. Sign in as one of the traders when Claude asks — it takes you to the service's own sign-in
   page. You see what that trader sees.
3. Attach **`…/market_events/_aggrs/board`** as a resource. That single read is the whole game.
4. **Subscribe** to **`…/market_events`**. Notifications carry no payload — they mean *re-read* —
   and are collapsed to at most one every few seconds, so a burst of trades does not become a
   burst of messages.
5. Start the agents. The board changes under you as they trade.

`market_events/_streams/newOffers` is the other kind of live: a websocket that pushes each
new offer in full, rather than a hint to re-read.

## Play it with agents

Give each agent [`agents/trader.md`](agents/trader.md) with `{{PLAYER}}`, `{{PASSWORD}}` and
`{{BASE_URL}}` substituted — the base URL is the service URL on the console's Connect page.
Each one discovers the API through `list_apis` / `how_to_call` and then sends the requests
itself, **as itself** — which is the point: `how_to_call` composes a request and deliberately
does not execute it, so the credentials stay with the caller.

A shared MCP connector carries one identity for everybody, so each agent needs its own session.
[`agents/mcp.sh`](agents/mcp.sh) opens and keeps one per user:

```bash
export MCP_BASE=https://<srvId>.<region>.restheart.com
./agents/mcp.sh trader1 "$TRADER1_PASSWORD" tools/call '{"name":"list_apis","arguments":{}}'
```

The endowments and objectives are cyclic on purpose. **Every good is held by two players** —
one with 12, one with 4 — and each objective asks for the good the *other* two hold, so nobody
is a monopolist and nobody can sit still. A trade that helps an opponent can still be the trade
that wins you the game, and an agent that refuses all of them loses slowly.

**Coin is a target, not a token.** Every objective asks for 70 coin and everyone starts with 60,
so no one can buy their way to their goods and still meet the threshold — something has to be
sold. There are 180 coin in the game and three thresholds of 70, so at most two players can hold
enough at once. That is what keeps coin worth having, and what stops the market from deadlocking
once the goods everyone wanted have moved.

## Files

| File | What it is |
|---|---|
| `rhc.setup.ts` | what the service must have, as steps that check and apply |
| `game/schema.ts` | the JSON Schema every ledger write is validated against |
| `game/ledger.ts` | the holdings derivation, the four aggregations, the change stream, the ledger's MCP block, the three genesis endowments |
| `game/rules.ts` | the six constraints, built on the derivation |
| `game/reference.ts` | items, players and objectives — metadata and data |
| `game/acl.ts` | the five permissions |
| `game/graphql.ts` | the GraphQL app |
| `agents/trader.md` | the prompt an agent plays with |
| `agents/mcp.sh` | a per-identity MCP client |

## Six things that will bite you

These are not hypothetical; every one of them broke this example before it worked.

1. **`path-prefix` matches whole segments.** `path-prefix('/market_')` matches *nothing* —
   no path has a segment equal to `market_`. The permissions spell out each collection.
   And a prefix really is needed, not `path()`: one collection answers at `/x`, `/x/_size`,
   `/x/{id}` **and** `/x/_aggrs/{name}`.
2. **`$eq: ["$field", null]` is false when the field is missing** — in *expressions*. So a
   `$cond` guarded that way fires on the wrong documents, and `$concatArrays` with one `null`
   element evaluates to `null`, silently emptying the whole array. Guard with
   `{"$eq": [{"$type": "$field"}, "object"]}`. Inside `$match` the query semantics differ and
   `{"field": {"$ne": null}}` is fine.
3. **An empty `filter` is rejected**, so a bulk delete of everything has to be spelled out:
   `filter={"_id":{"$exists":true}}`. That is what the fresh-game step does.
4. **Priority: a higher number wins.** Two permissions matched `/market_objectives` and the one
   carrying the `readFilter` had the *lower* number, so the request was still authorized — just
   by the wrong permission, silently, with no filter. `market_objectives` is now excluded from
   the public-read permission entirely: a security boundary should not depend on getting a
   priority number the right way round.
5. **`resources/subscribe` only does something on a collection.** Subscribing to an aggregation
   is accepted and then never fires, because notifications come from a change stream and only a
   collection has one. Subscribe to `market_events`; read the `board`.
6. **A check that only asks "does it exist?" hides your edits.** A permission whose predicate you
   changed under the same id still exists, so a naive setup would report the step satisfied and
   your change would never reach the service. Every check in `rhc.setup.ts` compares the stored
   document with the intended one, key by key.
