# shared helper, copied into each plugin dir (plugins are isolated from each other)
import importlib
import json
import sys
import traceback


def try_import(names):
    out = {}
    for n in names:
        try:
            m = importlib.import_module(n)
            out[n] = {"ok": True, "version": getattr(m, "__version__", None),
                      "file": getattr(m, "__file__", None)}
        except BaseException as e:  # noqa: BLE001 - we want to report everything
            out[n] = {"ok": False, "error": f"{type(e).__name__}: {e}"[:400]}
    return out


def run(checks):
    out = {}
    for name, fn in checks.items():
        try:
            out[name] = {"ok": True, "result": fn()}
        except BaseException as e:  # noqa: BLE001
            out[name] = {"ok": False, "error": f"{type(e).__name__}: {e}"[:400],
                         "tb": traceback.format_exc()[-600:]}
    return out


def respond(res, body):
    body["python"] = sys.version
    body["implementation"] = sys.implementation.name
    body["prefix"] = sys.prefix
    res.setContent(json.dumps(body, default=str))
    res.setContentTypeAsJson()
