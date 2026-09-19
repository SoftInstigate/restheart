# The market game

An application with no code. Three AI agents trade goods with each other through a RESTHeart Cloud
service's MCP server. Each has a secret objective and the wrong pile of goods. You follow the match
from Claude, or from a page that draws the board as they play.

It is a data model and nothing else: one collection, a schema, six rules, four aggregations, one
account and seven permissions. RESTHeart Cloud enforces it and publishes it over MCP, and the
agents are the user interface. **Model the domain, and the application is there.**

## Play it

You need a RESTHeart Cloud service, Node 22.18 or later, and Claude. Use a **fresh** service: one
set up for another app carries that app's Guards rules and user schema, and they apply here too.

### 1. Set up the service

Generate a personal access token at https://cloud.restheart.com/me/tokens, then:

```bash
npm install
npx rhc login                # paste the token
npx rhc setup --srv <srvId>  # the six characters at the start of your service URL
```

Wait about 20 seconds for the permissions to take effect. Run it again whenever you edit the game;
it changes only what differs. `--force game` deals a fresh board.

Check that the rules refuse what they must:

```bash
export MCP_BASE=https://<your service URL>
./agents/smoke.sh
```

It plays the legal moves and then the illegal ones, and checks every answer. It writes to the
ledger, so deal a fresh board afterwards.

### 2. Connect Claude

**Settings → Connectors → Add custom connector**, and paste the MCP endpoint from the console's
**MCP Server** page. Sign in on the service's own page as **`table`**, password
**`Aged-Harbour-Kettle-7`**.

Not as `root`: root bypasses the ACL, and the ACL is the game. No rule would match, `actor` would
never be stamped, and the players would have no identity.

Attach the resource ending in `/market_events/_aggrs/board` and subscribe to the one ending in
`/market_events`, so Claude is told when the ledger changes.

### 3. Start the game

One line, pasted into Claude:

```
Fetch the raw text of https://raw.githubusercontent.com/SoftInstigate/restheart/9.x/examples/market-game/agents/game.md, in full and not summarized, and do what it says.
```

Claude becomes the commentator and runs the match in rounds: each round it starts three subagents,
one per player, they make their moves and stop, and Claude tells you what happened. Nobody told
them the rules — they read them from the catalogue and from the errors they get back.

### 4. Watch it

Open [`watch.html`](watch.html) from disk. Nothing to install, and nothing to fill in but your
service URL. It draws the standings, the coin trade by trade, how each good is split between the
three players, the open offers and the match round by round, and follows along while they play.

**Replay** walks the match again at a few frames a second. **Show the private objectives** unlocks
all three and spoils the ending, which is the privilege of the commentator.

### 5. Close the game when you stop

The password is published, so a service left set up is a service anybody can write to:

```bash
npx rhc setup --srv <srvId> --file rhc.close.ts
```

It revokes the three rules that allow a POST. Reading stays, so the page still draws the finished
match, but the ledger cannot grow. The ordinary setup puts the rules back.

## Three players, one connection

A connector carries one identity, and this game is built on knowing who wrote what. Three subagents
behind it would be one player with three voices.

So the account is nobody in particular, and two arguments on the call say who is speaking:

```json
"args": { "trader": "trader1", "secret": "seagull-brick-oath", "body": { ...the event... } }
```

There is one permission per player holding that player's name and secret, so a call is `trader1`
only when both match, and the server stamps `actor` from the rule itself. **Each subagent is told
its own secret and no other**, which is the whole of the separation. The commentator holds all
three, so it can read any objective; the players cannot read each other's.

The two private things are written differently on purpose. Appending to the ledger is a
**predicate**: no pair, no write. Reading your own state is a **filter**, or a branch inside an
aggregation: the rule lets anyone ask and a wrong pair returns nothing. A predicate there would
hide the resource, because a catalogue is built by asking the ACL what a caller may read and that
question carries no arguments.

The secrets are in the open in `game/reference.ts`, to be copied into prompts. Do not read it as a
pattern. When a client can hold a credential of its own, give it an account of its own.

## How it works

One collection, `market_events`, append-only. Four kinds of event: `genesis` what a player starts
with, `offer` "I give X for Y", `trade` somebody accepted one, `claim` "I won". Everything else —
holdings, open offers, prices, standings — is derived from it by aggregations. Nothing is ever
updated or deleted.

**Collisions settle themselves.** A trade's `_id` is derived from the offer's, so the second player
to accept the same offer gets a `409`. A claim has the fixed `_id` `win`.

**Six rules guard the rest**, each an aggregation that runs inside the write's transaction: no
negative holdings, no offering more than you hold, no trade without a real offer, no accepting your
own, no claiming a victory the board does not show, and nothing at all after somebody has won. A
JSON Schema caps an offer at 3 units or 30 coin, so winning takes several trades.

**A `409` says which of three things happened**: `"retryable": true` means two writes collided and
yours was not applied, send it again; a `"constraint"` means a rule refused it; neither means that
`_id` exists already.

**The game cannot stall.** Each good is held by two players, 12 and 4, and each objective asks for
what the other two hold. The thresholds are uneven on purpose: two players must reach 70 coin
holding 60, so they have to sell, while the third only needs 40 and is the one who can pay cash.
Make them all short of coin and the market freezes the moment the goods are distributed, because
then no purchase helps the buyer. That third player pays for its cash with the hardest goods
target, 14 ore of the 16 that exist.

## Files

| File | What it is |
|---|---|
| `rhc.setup.ts` | what the service must have, as steps that check and apply |
| `rhc.close.ts` | the same for a service nobody is playing on: revokes every write |
| `game/service.ts` | comparing what a service holds with what is meant, for both setups |
| `game/schema.ts` | the JSON Schema for ledger events |
| `game/ledger.ts` | the derivation, the four aggregations, the change stream, the endowments |
| `game/rules.ts` | the six constraints |
| `game/reference.ts` | items, players, objectives, the account and the secrets |
| `game/acl.ts` | the permissions, and the trader/secret pairs |
| `game/graphql.ts` | the GraphQL app |
| `agents/game.md` | the prompt: a commentator that runs rounds of three subagents |
| `watch.html` | the spectator page: open it from disk, no build, no dependencies |
| `agents/mcp.sh` | a minimal MCP client, for checking from a terminal |
| `agents/smoke.sh` | every rule exercised as the three players, each answer checked |

The console shows all of it: the rules on the **Constraints** page, where **Run** tries each against
the data, and the resources on the **MCP Server** page.

More in the RESTHeart Cloud manual: [MCP Server](https://restheart.org/docs/cloud/mcp), [Data
Constraints](https://restheart.org/docs/cloud/constraints), [the `rhc`
CLI](https://restheart.org/docs/cloud/cli).

## Things that bit us

1. **`path-prefix` matches whole path segments.** `path-prefix('/market_')` matches nothing.
2. **`$eq: ["$field", null]` is false when the field is missing**, in expressions. Test
   `{"$eq": [{"$type": "$field"}, "object"]}` instead.
3. **A bulk delete needs a filter.** To delete everything: `filter={"_id":{"$exists":true}}`.
4. **`$lookup` is blacklisted in aggregations.** Write the table into the pipeline instead.
5. **`@qparams['x']` works in a filter, never in a predicate** — the permission would not even
   load. Use `%{q,x}` there.
6. **Subscribing to an aggregation never notifies.** Subscribe to the collection, read the
   aggregation.
7. **Between two matching permissions the higher priority wins.** Do not rely on that for a read
   filter: give the filtered resource its own permission.
8. **"Does it exist?" is not "is it what I meant?"** The setup compares stored documents with the
   intended ones, so an edited rule is re-applied.
