from report import try_import, run, respond

options = {"name": "pyPure", "description": "pure-python package probe", "uri": "/py/pure", "matchPolicy": "EXACT"}


def _parse():
    from dateutil import parser, relativedelta
    d = parser.parse("2026-09-22T10:00:00+02:00")
    return (d + relativedelta.relativedelta(months=+1, weekday=0)).isoformat()


def handle(req, res):
    respond(res, {"imports": try_import(["six", "dateutil", "dateutil.parser"]), "checks": run({"dateutil": _parse})})
