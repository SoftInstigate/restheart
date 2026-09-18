# The market game

An application with no code. Three AI agents trade goods with each other through a RESTHeart
Cloud service's MCP server. Each has a secret objective and the wrong pile of goods. You watch
the board from Claude.

Nothing here is a program. It is a data model: one collection, a schema, six rules, three
aggregations, one account and nine permissions. RESTHeart Cloud enforces it and publishes it
over MCP, and the agents are the user interface. That is the point of the example: **model the
domain, and the application is there.**

## Play it

You need a RESTHeart Cloud service, Node 22.18 or later, and Claude. A free service is fine, but
make it a **fresh** one: a service set up for another app carries that app's Guards rules and
user schema, and they apply to the players too.

### 1. Set up the service

```bash
npm install
npx rhc login                       # paste a personal access token from your console profile
npx rhc setup --srv <srvId>         # the six characters at the start of your service URL
```

Any token from https://cloud.restheart.com/me/tokens works: there is no role to choose and no
permission to add. Every token is issued with the `cli` role, and `rhc` uses it to mint an admin
token for a service you own. It is your **cloud** credential, for the setup only — the agents
never see it, and sign in with something else entirely.

That is the whole setup: the ledger and its rules, three aggregations, a change stream, a GraphQL
app, one account and the permissions. Wait about 20 seconds after the first run for the
permissions to take effect. Run it again any time, it changes only what differs. To wipe the
ledger and start a new game: `npx rhc setup --srv <srvId> --force game`.

Check that the rules refuse what they must:

```bash
export MCP_BASE=https://<your service URL>
./agents/smoke.sh
```

It plays the moves that must go through, then an offer over the cap, an over-commitment, a
self-deal, a trade on a missing offer, a false claim of victory and a second acceptance, and
checks each answer. It appends to the ledger, so `--force game` afterwards for a clean board.

### 2. Connect Claude

**Settings → Connectors → Add custom connector**, and paste the MCP endpoint from the console's
**MCP Server** page. Claude opens the service's own sign-in page: sign in as **`table`**, password
**`Aged-Harbour-Kettle-7`**. It is a user *of the service*, created by the setup.

Not as `root`, and not with the token from step 1. Root bypasses the ACL, and the ACL is the
game: no rule matches, so `actor` is never stamped on an event and every objective is readable by
everyone. The players would have no identity and no secrets.

Then attach the resource ending in `/market_events/_aggrs/board` — that one read is the whole
game — and subscribe to the one ending in `/market_events`, so Claude is told when the ledger
changes and re-reads the board.

### 3. Start the game

Paste [`agents/game.md`](agents/game.md) into Claude as it is. That is the only prompt, and there
is nothing to fill in: Claude takes the service URL from the connector, or asks you for it.

Claude becomes the commentator and runs the match in rounds: each round it starts three
subagents, one per player, each with its own name and secret; they make their moves and stop;
Claude reads the board and tells you what happened. Then the next round. Four agents, one
connection.

A round takes a couple of minutes, and you get the commentary at the end of each one. If Claude
instead sits silent for a quarter of an hour, it has started players that were told to play the
whole match rather than one round: stop it and give it the prompt again.

They discover the API, make offers, accept each other's, and one of them eventually claims
victory. Nobody told them how the game works: they read it from the catalogue, the rules and the
errors they get back.

## Three players, one connection

A connector carries one identity for the whole application, and this game is built on knowing who
wrote what: holdings, offers and victory are all computed from the `actor` of each event. Three
subagents behind one connector would be one player with three voices.

So the account is nobody in particular. On its own it reads the public game and nothing else.
Two arguments on the call say who is speaking:

```json
"args": { "trader": "trader1", "secret": "seagull-brick-oath", "body": { ...the event... } }
```

The permissions hold one rule per player, with that player's name and secret written into the
same rule, so a call is `trader1` only when both match. The server then stamps `actor: trader1`
from the rule itself: what a player signs is decided by the secret they proved, not by anything
they put in the body. Wrong secret, or none, and the call is refused.

**Each subagent is told its own secret and no other.** That is the whole of the separation, and
it is the part a prompt can carry that a shared connection cannot. The commentator holds all
three, so it can read any objective and call the match properly; the players cannot read each
other's.

Do not give the commentator a wider credential instead. It would be the connection's identity,
therefore the players' too, and a root account bypasses the ACL: no rule would match, `actor`
would never be stamped, and every move would be refused by the schema that requires it.

The two private things are written differently on purpose. Appending to the ledger is a
**predicate**: no pair, no write, and the rule that matched is what stamps `actor`. Reading your
objective is a **filter**: the rule lets any player ask, and `@qparams['trader']` and
`@qparams['secret']` are substituted into the query, so a wrong pair returns nothing instead of a
refusal. A predicate there would hide the collection, since a catalogue is built by asking the ACL
what a caller may read and that question carries no arguments.

The secrets are in the open, in `game/reference.ts`, to be copied into prompts. Do not read it as
a pattern: a secret in a query string is read by every request log and by whoever holds the
transcript. When a client can hold a credential of its own, give it an account of its own.

## How it works

One collection, `market_events`, append-only, the only thing anybody writes to. Four kinds of
event: `genesis` what a player starts with, `offer` "I give X for Y", `trade` somebody accepted
one, `claim` "I won". Everything else — who owns what, which offers are open, who is winning — is
computed from it by aggregations. Nothing is ever updated or deleted.

**Collisions settle themselves.** A trade's `_id` is derived from the offer's, so the second
player to accept the same offer gets a `409`. A victory claim has the fixed `_id` `win`, so only
one can exist.

**Six rules guard the rest**, each an aggregation that runs inside the write's transaction:
no negative holdings, no offering more than you hold, no trade without a real offer, no accepting
your own, no claiming a victory the board does not show, and nothing at all after somebody has
won. A JSON Schema caps an offer at 3 units or 30 coin, so winning takes several trades.

**A `409` says which of three things happened**: `"retryable": true` means two writes collided
and yours was not applied, send it again; a `"constraint"` means a rule refused it, re-read the
board; neither means that `_id` exists already, somebody was faster.

**The game cannot stall.** Every good is held by two players, one with 12 and one with 4, and each
objective asks for what the other two hold. Everyone starts 10 coin short of their own target and
there is not enough coin for all three, so something always has to be sold.

## Files

| File | What it is |
|---|---|
| `rhc.setup.ts` | what the service must have, as steps that check and apply |
| `game/schema.ts` | the JSON Schema for ledger events |
| `game/ledger.ts` | the holdings derivation, the aggregations, the change stream, the endowments |
| `game/rules.ts` | the six constraints |
| `game/reference.ts` | items, players, objectives, the account and the secrets |
| `game/acl.ts` | the permissions, and the trader/secret pairs |
| `game/graphql.ts` | the GraphQL app |
| `agents/game.md` | the prompt: a commentator that runs rounds of three subagents |
| `agents/mcp.sh` | a minimal MCP client, for checking from a terminal |
| `agents/smoke.sh` | every rule exercised as the three players, each answer checked |

Everything is in the console afterwards: the rules on the **Constraints** page, where **Run**
tries each against the data; the resources on the **MCP Server** page, under **Test
credentials**.

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
