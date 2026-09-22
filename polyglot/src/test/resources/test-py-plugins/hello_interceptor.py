import json

options = {
    "name": "pyHelloWorldInterceptor",
    "description": "modifies the response of pyHelloWorldService",
    "interceptPoint": "RESPONSE"
}


def handle(req, res):
    body = json.loads(res.getContent())
    body["note"] = "modified by pyHelloWorldInterceptor"
    res.setContent(json.dumps(body))


def resolve(req):
    return req.isHandledBy("pyHelloWorldService") and req.isGet()
