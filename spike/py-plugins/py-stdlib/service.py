from report import try_import, run, respond

options = {"name": "pyStdlib", "description": "stdlib probe", "uri": "/py/stdlib", "matchPolicy": "EXACT"}

MODULES = ["json", "re", "datetime", "decimal", "hashlib", "hmac", "base64", "zlib", "bz2", "lzma",
           "uuid", "statistics", "math", "random", "csv", "sqlite3", "ssl", "socket", "urllib.request",
           "threading", "asyncio", "ctypes", "multiprocessing", "subprocess", "xml.etree.ElementTree",
           "unicodedata", "struct", "array", "pickle", "zoneinfo", "tomllib", "logging", "venv", "pip"]


def _sqlite():
    import sqlite3
    c = sqlite3.connect(":memory:")
    c.execute("create table t(x)")
    c.executemany("insert into t values (?)", [(i,) for i in range(10)])
    return c.execute("select sum(x) from t").fetchone()[0]


def _threads():
    import threading
    out = []
    ts = [threading.Thread(target=lambda i=i: out.append(i * i)) for i in range(4)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    return sorted(out)


def _subprocess():
    import subprocess
    return subprocess.run(["echo", "hi"], capture_output=True, text=True).stdout.strip()


def _java_interop():
    import java
    BsonDocument = java.type("org.bson.BsonDocument")
    return BsonDocument.parse('{"a": 1}').toJson()


CHECKS = {
    "sha256": lambda: __import__("hashlib").sha256(b"restheart").hexdigest()[:16],
    "zlib": lambda: len(__import__("zlib").compress(b"x" * 1000)),
    "decimal": lambda: str(__import__("decimal").Decimal("0.1") + __import__("decimal").Decimal("0.2")),
    "sqlite_memory": _sqlite,
    "threads": _threads,
    "subprocess_echo": _subprocess,
    "java_interop_bson": _java_interop,
    "pluginArgs_global": lambda: repr(pluginArgs),
    "LOGGER_global": lambda: LOGGER.getName(),
}


def handle(req, res):
    respond(res, {"imports": try_import(MODULES), "checks": run(CHECKS)})
