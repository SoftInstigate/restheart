import json

options = {
    "name": "pyHelloWorldService",
    "description": "just another Hello World, in Python",
    "uri": "/pyhello",
    "secured": False,
    "matchPolicy": "EXACT"
}


def handle(req, res):
    if req.isGet():
        res.setContent(json.dumps({"msg": "Hello World!"}))
        res.setContentTypeAsJson()
