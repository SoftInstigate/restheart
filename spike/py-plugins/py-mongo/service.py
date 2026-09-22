import java
from report import run, respond

options = {"name": "pyMongo", "description": "MongoDB access from Python", "uri": "/py/mongo", "matchPolicy": "EXACT"}

Document = java.type("org.bson.Document")


def _roundtrip():
    coll = mclient.getDatabase("restheart").getCollection("spike")
    coll.insertOne(Document("msg", "from python").append("n", 42))
    return {"count": coll.countDocuments(),
            "docs": [str(d.toJson()) for d in coll.find().limit(3)]}


def handle(req, res):
    respond(res, {"has_mclient": "mclient" in dir(__builtins__) or "mclient" in globals(),
                  "checks": run({"mongo_roundtrip": _roundtrip})})
