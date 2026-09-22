#!/usr/bin/env bash
# Starts RESTHeart (standalone, no MongoDB) with only the given plugin dirs
# deployed, then records startup, per-plugin deploy time, latency and memory.
# usage: spike/measure.sh <label> <url-path|-> [plugin-dir ...]
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL=$1; URL=$2; shift 2
P=core/target/plugins
OUT=../runs/$LABEL; mkdir -p "$OUT"

for d in "$P"/*/; do [ -f "$d/package.json" ] && rm -rf "$d"; done
for src in "$@"; do cp -r "$src" "$P/$(basename "$src")"; done

T0=$(python3 -c 'import time; print(time.time())')
spike/run-rh.sh rh -s
until docker logs rh 2>&1 | grep -qE "RESTHeart started|RESTHeart stopped"; do sleep 1; done
T1=$(python3 -c 'import time; print(time.time())')
docker logs rh > "$OUT/startup.log" 2>&1
python3 - "$OUT/startup.log" "$T0" "$T1" > "$OUT/startup.json" <<'EOF'
import re, sys, json
from datetime import datetime
log = open(sys.argv[1], encoding="utf-8", errors="replace").read()
ts = lambda s: datetime.strptime(s, "%H:%M:%S.%f")
lines = [l for l in log.splitlines() if re.match(r"\d\d:\d\d:\d\d\.\d{3}", l)]
start = ts(lines[0][:12])
found = [ts(l[:12]) for l in lines if "Found js plugin directory" in l]
deployed = [(re.sub(r"\x1b\[[0-9;]*m", "", l)[13:].split(" deployed")[0], ts(l[:12])) for l in lines if " deployed" in l]
started = [ts(l[:12]) for l in lines if "RESTHeart started" in l]
res = {"wall_to_started_s": round(float(sys.argv[3]) - float(sys.argv[2]), 1),
       "log_start_to_started_s": round((started[0] - start).total_seconds(), 2) if started else None,
       "errors": len(re.findall(r"ERROR", log))}
prev = max(found) if found else None
for name, t in deployed:
    if prev:
        res[name] = round((t - prev).total_seconds(), 2)
    prev = t
print(json.dumps(res))
EOF
cat "$OUT/startup.json"
docker stats --no-stream rh --format '{"rss_after_start":"{{.MemUsage}}"}' | tee "$OUT/mem-start.json"
if [ "$URL" != "-" ]; then
  python3 spike/bench.py "$URL" "${WARMUP:-2000}" "${N:-1000}" "${CONC:-8}" | tee "$OUT/latency.json"
  docker stats --no-stream rh --format '{"rss_after_load":"{{.MemUsage}}"}' | tee "$OUT/mem-load.json"
fi
docker exec rh jcmd 1 GC.heap_info > "$OUT/heap.txt" 2>&1 || true
docker rm -f rh >/dev/null
