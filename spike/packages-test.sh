#!/usr/bin/env bash
# Deploys the package-probe Python plugins (each with its own venv) against a
# running MongoDB and records what each one reports.
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=../runs/packages; mkdir -p "$OUT"
P=core/target/plugins

RHO='/mclient/connection-string->"mongodb://mongo";/http-listener/host->"0.0.0.0"' spike/run-rh.sh rh
until docker logs rh 2>&1 | grep -qE "RESTHeart started|RESTHeart stopped"; do sleep 2; done
docker logs rh > "$OUT/startup.log" 2>&1
grep -E "deployed|Error deploying|ERROR" "$OUT/startup.log" | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-200

for u in stdlib pure numpy pydantic mongo; do
  echo "--- /py/$u"
  curl -s -m 120 "localhost:8080/py/$u" | tee "$OUT/$u.json" | python3 -m json.tool 2>/dev/null | head -60
  echo
done
docker stats --no-stream rh --format 'rss={{.MemUsage}}' | tee "$OUT/mem.txt"
