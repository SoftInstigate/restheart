#!/usr/bin/env bash
# A minimal MCP client, for checking the game from a terminal. The agents use the connector.
#
#   MCP_BASE=https://<srvId>.<region>.restheart.com ./mcp.sh <method> [params-json]
#
#   ./mcp.sh tools/list
#   ./mcp.sh tools/call     '{"name":"list_apis","arguments":{}}'
#   ./mcp.sh resources/read '{"uri":"'"$MCP_BASE"'/market_events/_aggrs/board"}'
#
# One account plays for everybody, so this opens one session. Which player is speaking is decided
# per call by the `trader` and `secret` arguments — see game/reference.ts:
#
#   ./mcp.sh tools/call '{"name":"call_api","arguments":{
#      "resource":"'"$MCP_BASE"'/market_objectives","action":"query",
#      "args":{"trader":"trader1","secret":"seagull-brick-oath"}}}'
#
# The session id is cached and re-established if the server has forgotten it.
set -euo pipefail

BASE="${MCP_BASE:?set MCP_BASE to your service URL, the one shown on the Connect page in the console, e.g. https://abc123.eu-central-1-free-1.restheart.com}"
# the account and its password are in game/reference.ts, in the open: it is a game
USER_ID="table"; PASSWORD="Aged-Harbour-Kettle-7"; METHOD="$1"; PARAMS="${2-}"
[ -n "$PARAMS" ] || PARAMS='{}'

SESSION_FILE="${TMPDIR:-/tmp}/mcp-session-$USER_ID"
ACCEPT='Accept: application/json, text/event-stream'

init() {
  curl -s -D - -u "$USER_ID:$PASSWORD" -H 'Content-Type: application/json' -H "$ACCEPT" \
    -X POST "$BASE/mcp" -o /dev/null \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"trader","version":"1"}}}' \
    | grep -i '^mcp-session-id' | tr -d '\r' | awk '{print $2}' > "$SESSION_FILE"
  [ -s "$SESSION_FILE" ] || { echo "cannot open an MCP session as $USER_ID (wrong password, or /mcp denied)" >&2; exit 1; }
  curl -s -u "$USER_ID:$PASSWORD" -H 'Content-Type: application/json' -H "$ACCEPT" \
    -H "Mcp-Session-Id: $(cat "$SESSION_FILE")" -X POST "$BASE/mcp" -o /dev/null \
    -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
}

call() {
  curl -s -u "$USER_ID:$PASSWORD" -H 'Content-Type: application/json' -H "$ACCEPT" \
    -H "Mcp-Session-Id: $(cat "$SESSION_FILE")" -w '\n%{http_code}' -X POST "$BASE/mcp" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"$METHOD\",\"params\":$PARAMS}"
}

[ -s "$SESSION_FILE" ] || init
OUT="$(call)"; CODE="$(tail -n1 <<<"$OUT")"
if [ "$CODE" = "404" ] || [ "$CODE" = "400" ]; then init; OUT="$(call)"; CODE="$(tail -n1 <<<"$OUT")"; fi

# A resource the caller may not read is refused at the transport level, with no JSON-RPC body.
if [ "$CODE" = "403" ]; then echo "DENIED (HTTP 403): your ACL does not allow that" >&2; exit 3; fi
if [ "$CODE" != "200" ]; then echo "HTTP $CODE" >&2; exit 4; fi

sed '$d' <<<"$OUT" | python3 -c '
import sys, json, re
raw = sys.stdin.read()
m = re.search(r"^data: (.*)$", raw, re.M)
d = json.loads(m.group(1) if m else raw)
if "error" in d:
    print("ERROR:", d["error"].get("message"), file=sys.stderr); sys.exit(5)
r = d.get("result", {})
if r.get("isError"):
    print("TOOL ERROR:", r["content"][0]["text"], file=sys.stderr); sys.exit(6)
if "contents" in r:  # resources/read
    print(r["contents"][0]["text"])
elif "content" in r:  # tools/call
    print(r["content"][0]["text"])
else:
    print(json.dumps(r))
'
