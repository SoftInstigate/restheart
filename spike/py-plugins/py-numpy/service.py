from report import try_import, run, respond

options = {"name": "pyNumpy", "description": "numpy probe", "uri": "/py/numpy", "matchPolicy": "EXACT"}


def _numpy():
    import numpy as np
    a = np.arange(1_000_000, dtype=np.float64)
    m = np.random.default_rng(42).standard_normal((200, 200))
    return {"mean": float(a.mean()), "det_sign": float(np.sign(np.linalg.det(m))), "version": np.__version__}


def handle(req, res):
    respond(res, {"imports": try_import(["numpy"]), "checks": run({"numpy": _numpy})})
