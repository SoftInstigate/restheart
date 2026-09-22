from report import try_import, run, respond

options = {"name": "pyPydantic", "description": "pydantic probe", "uri": "/py/pydantic", "matchPolicy": "EXACT"}


def _pydantic():
    from pydantic import BaseModel, ValidationError

    class Order(BaseModel):
        id: int
        qty: int = 1
        tags: list[str] = []

    ok = Order.model_validate({"id": "7", "tags": ["a"]}).model_dump()
    try:
        Order.model_validate({"id": "x"})
        err = None
    except ValidationError as e:
        err = e.errors()[0]["type"]
    return {"ok": ok, "error_type": err}


def handle(req, res):
    respond(res, {"imports": try_import(["pydantic_core", "pydantic"]), "checks": run({"pydantic": _pydantic})})
