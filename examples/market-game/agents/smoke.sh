#!/usr/bin/env bash
# Plays the moves that must work and the moves that must be refused, as the three traders, and
# checks every answer. Run it after `rhc setup`, and again after every change to the rules.
#
#   MCP_BASE=https://<srvId>.<region>.restheart.com ./agents/smoke.sh
#
# It appends to the ledger. Start from a fresh game (`rhc setup --srv <id> --force game`) to
# see the same result twice.
set -uo pipefail

BASE="${MCP_BASE:?set MCP_BASE to the service URL}"
# one account plays for everybody; who is speaking is the trader/secret pair on each call
TABLE_USER=table
TABLE_PASSWORD='Aged-Harbour-Kettle-7'
secret_of() {
  case "$1" in
    trader1) echo 'seagull-brick-oath' ;;
    trader2) echo 'copper-lantern-drift' ;;
    trader3) echo 'velvet-anchor-moss' ;;
  esac
}
RUN="$(date +%s)"          # unique ids per run, so a rerun does not trip over its own offers
PASS=0; FAIL=0
BASE="${BASE%/}"

# Reachability first, with curl's own words: a wrong or unreachable MCP_BASE shows up as a
# "000" on every line otherwise, which says nothing about why.
if ! err="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/ping" 2>&1)" || [ "$err" != 200 ]; then
  echo "cannot reach $BASE/ping: ${err:-no answer}" >&2
  echo "MCP_BASE must be the service URL from the console's Connect page, scheme included, e.g. https://d36c92.eu-central-1-free-1.restheart.com" >&2
  exit 1
fi

# Then the one read that tells the systemic failures apart, before ten writes repeat the same
# answer ten times. Everything below assumes a trader can reach the game at all.
probe="$(curl -s -o /dev/null -w '%{http_code}' -u "$TABLE_USER:$TABLE_PASSWORD" \
  "$BASE/market_objectives?trader=trader1&secret=$(secret_of trader1)")"
case "$probe" in
  200) ;;
  401) echo "the table account cannot sign in (401): it was never created — run rhc setup." >&2; exit 1 ;;
  403) echo "trader1 is refused (403): the permissions are not in effect yet. They take up to 20 seconds after rhc setup; try again." >&2; exit 1 ;;
  451) echo "trader1 is blocked (451): this service has a Guards rule — the consents gate of another app's setup — that stops every user who has not accepted its terms, the traders included." >&2
       echo "Use a fresh service for the game, or exempt role 'trader' in that rule (console → Guards)." >&2; exit 1 ;;
  *)   echo "trader1 gets $probe reading /market_objectives; something is off with the service before the game even starts." >&2; exit 1 ;;
esac

# post <trader> <json> → prints "<status> <body>"
post() {
  local who="$1"
  curl -s -u "$TABLE_USER:$TABLE_PASSWORD" -H 'Content-Type: application/json' -w '\n%{http_code}' \
    -X POST "$BASE/market_events?trader=$who&secret=$(secret_of "$who")" -d "$2" | python3 -c '
import sys; lines = sys.stdin.read().rstrip("\n").split("\n"); print(lines[-1], "".join(lines[:-1]))'
}

# expect <label> <status> <constraint-or-"-"> <who> <json>
expect() {
  local label="$1" want="$2" rule="$3" who="$4" body="$5"
  local out; out="$(post "$who" "$body")"
  local status="${out%% *}" resp="${out#* }"
  local got_rule; got_rule="$(python3 -c 'import json,sys
try: print(json.loads(sys.argv[1]).get("constraint","-"))
except Exception: print("-")' "$resp")"
  if [ "$status" = "$want" ] && [ "$got_rule" = "$rule" ]; then
    PASS=$((PASS+1)); printf '  ok   %-58s %s %s\n' "$label" "$status" "$([ "$rule" = - ] || echo "$rule")"
  else
    FAIL=$((FAIL+1)); printf '  FAIL %-58s wanted %s %s, got %s %s\n' "$label" "$want" "$rule" "$status" "$got_rule"
    if [ "$resp" = "${LAST_FAILURE:-}" ]; then
      printf '       (same answer as above)\n'
    else
      printf '       %s\n' "${resp:0:220}"
    fi
    LAST_FAILURE="$resp"
  fi
}

offer() { printf '{"_id":"offer:%s:%s","offerId":"offer:%s:%s","type":"offer","give":{"item":"%s","qty":%s},"want":{"item":"%s","qty":%s}}' "$1" "$2" "$1" "$2" "$3" "$4" "$5" "$6"; }
accept() { printf '{"_id":"accept:%s","offerId":"%s","type":"trade"}' "$1" "$1"; }

echo "== moves that must work"
expect "trader1 offers 3 grain for 3 ore"                 201 - trader1 "$(offer trader1 "$RUN-1" grain 3 ore 3)"
expect "trader3 offers 3 ore for 3 silk (holds 4 ore)"    201 - trader3 "$(offer trader3 "$RUN-1" ore 3 silk 3)"
expect "trader2 accepts trader1's offer"                  201 - trader2 "$(accept "offer:trader1:$RUN-1")"

echo "== moves the schema must refuse"
expect "an offer of 4 grain (cap is 3)"                   400 - trader1 "$(offer trader1 "$RUN-x" grain 4 ore 1)"
expect "an event of an unknown type"                      400 - trader1 '{"_id":"weird:'"$RUN"'","offerId":"weird:'"$RUN"'","type":"gift"}'

echo "== moves the rules must refuse"
expect "trader3 offers 3 more ore (would commit 6 of 4)"  409 noOverCommitment  trader3 "$(offer trader3 "$RUN-2" ore 3 coin 10)"
expect "trader3 accepts its own offer"                    409 noSelfDealing     trader3 "$(accept "offer:trader3:$RUN-1")"
expect "a trade on an offer that does not exist"          409 tradeSettlesAnOffer trader2 "$(accept "offer:nobody:$RUN")"
expect "trader1 claims victory with 3 ore of 10"          409 claimIsEarned     trader1 '{"_id":"win","offerId":"win","type":"claim"}'

echo "== the unique _id"
expect "trader3 accepts an offer already accepted"        409 - trader3 "$(accept "offer:trader1:$RUN-1")"

echo "== the objectives, which the filter keeps private"
objective() {
  curl -s -u "$TABLE_USER:$TABLE_PASSWORD" "$BASE/market_objectives?trader=$1&secret=$2" | python3 -c '
import json, sys
try: docs = json.loads(sys.stdin.read())
except Exception: print("unreadable"); raise SystemExit
if not isinstance(docs, list): print("unreadable"); raise SystemExit
if not docs: print("none"); raise SystemExit
print(",".join(sorted(d.get("player", "?") + ("+secret" if "secret" in d else "") for d in docs)))'
}
check() {
  local label="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then PASS=$((PASS+1)); printf '  ok   %-58s %s\n' "$label" "$got"
  else FAIL=$((FAIL+1)); printf '  FAIL %-58s wanted %s, got %s\n' "$label" "$want" "$got"; fi
}
check "trader1 with its own secret reads its own objective" trader1 "$(objective trader1 "$(secret_of trader1)")"
check "trader1 with trader2's secret reads nothing"         none    "$(objective trader1 "$(secret_of trader2)")"
check "no secret at all reads nothing"                      none    "$(objective trader1 '')"
check "trader2 with its own secret reads its own objective" trader2 "$(objective trader2 "$(secret_of trader2)")"

echo "== the board, as trader2"
board_out="$(curl -s -u "$TABLE_USER:$TABLE_PASSWORD" -w '\n%{http_code}' "$BASE/market_events/_aggrs/board")"
board_status="${board_out##*$'\n'}"; board_body="${board_out%$'\n'*}"
if [ "$board_status" != 200 ]; then
  echo "  cannot read the board: $board_status ${board_body:0:200}"
else
  python3 - "$board_body" <<'BOARD'
import json, sys
board = json.loads(sys.argv[1])[0]
for h in board["holdings"]:
    goods = " ".join(f"{g['item']}={g['qty']}({g['available']})" for g in h["goods"])
    print("  ", h["_id"], goods)
print("   open offers:", len(board["openOffers"]), "| settled:", len(board["settledTrades"]), "| winner:", board["winner"] or "none")
BOARD
fi

echo
echo "passed $PASS, failed $FAIL"
[ "$FAIL" -eq 0 ]
