import {
    defineService, BadRequestError, UnauthorizedError, ForbiddenError, NotFoundError, ConflictError, HttpError
} from "restheart:sdk";

// reference plugin: typed errors become their status, anything else a 500 with nothing leaked
export default defineService({
    name: "sdkErrors",
    description: "conformance: typed errors and uncaught exceptions",
    uri: "/sdk/errors",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        switch (req.query.kind) {
            case "bad": throw new BadRequestError("wrong limit qparam");
            case "unauthorized": throw new UnauthorizedError();
            case "forbidden": throw new ForbiddenError("not yours");
            case "notfound": throw new NotFoundError("no such thing");
            case "conflict": throw new ConflictError("already there");
            case "teapot": throw new HttpError(418, "short and stout");
            case "boom": throw new Error("a secret the client must not see");
            case "typeerror": return req.content.user.name.toUpperCase();
            case "setInError": res.setInError(422, "set by setInError"); return;
            default: res.json({ ok: true });
        }
    }
});
