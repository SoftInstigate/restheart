#!/usr/bin/env python3
"""Rough latency probe: first requests, then warm sequential and concurrent latency.

usage: spike/bench.py <path> [warmup] [n] [concurrency]
"""
import http.client
import json
import statistics
import sys
import threading
import time

HOST, PORT = "localhost", 8080


def timed_get(conn, path):
    t = time.perf_counter()
    conn.request("GET", path)
    r = conn.getresponse()
    body = r.read()
    return (time.perf_counter() - t) * 1000, r.status, body


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(p / 100 * (len(xs) - 1))))]


def main():
    path = sys.argv[1]
    warmup = int(sys.argv[2]) if len(sys.argv) > 2 else 2000
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 1000
    conc = int(sys.argv[4]) if len(sys.argv) > 4 else 8
    conn = http.client.HTTPConnection(HOST, PORT)

    first = []
    for _ in range(16):
        ms, status, body = timed_get(conn, path)
        first.append(round(ms, 1))
    assert status == 200, (status, body)

    t_warm = time.perf_counter()
    for _ in range(warmup):
        timed_get(conn, path)
    warmup_s = time.perf_counter() - t_warm

    seq = [timed_get(conn, path)[0] for _ in range(n)]

    lat = []
    lock = threading.Lock()

    def worker():
        c = http.client.HTTPConnection(HOST, PORT)
        mine = [timed_get(c, path)[0] for _ in range(n // conc)]
        with lock:
            lat.extend(mine)

    t0 = time.perf_counter()
    ts = [threading.Thread(target=worker) for _ in range(conc)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    wall = time.perf_counter() - t0

    print(json.dumps({
        "path": path,
        "body": body.decode()[:120],
        "first16_ms": first,
        "warmup_requests": warmup,
        "warmup_s": round(warmup_s, 1),
        "seq_n": n,
        "seq_p50_ms": round(pct(seq, 50), 2),
        "seq_p99_ms": round(pct(seq, 99), 2),
        "seq_mean_ms": round(statistics.mean(seq), 2),
        "conc": conc,
        "conc_p50_ms": round(pct(lat, 50), 2),
        "conc_p99_ms": round(pct(lat, 99), 2),
        "conc_rps": round(len(lat) / wall, 1),
    }))


if __name__ == "__main__":
    main()
