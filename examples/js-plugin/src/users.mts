import { defineService, mongo, logger, config, BadRequestError } from "restheart:sdk";

// the mongo capability: plain values in, plain values out, blocking, no promises
//
// configuration, in restheart.yml, by plugin name:
//
//   usersService:
//     db: restheart
//     collection: users
export default defineService({
    name: "usersService",
    description: "lists the users of a collection, with limit, skip and filter",
    uri: "/users",
    secured: true,
    matchPolicy: "EXACT",
    contentKind: "json",

    handle(req, res) {
        const limit = Number(req.query.limit ?? 100);
        const skip = Number(req.query.skip ?? 0);

        if (Number.isNaN(limit) || Number.isNaN(skip)) {
            throw new BadRequestError("limit and skip must be numbers");
        }

        // a filter is extended JSON in the query string, as everywhere in RESTHeart
        const filter = req.query.filter ? JSON.parse(req.query.filter) : {};

        // null-safe: req.account is null when not authenticated
        logger.debug("listing users for {}", req.account?.name);

        const db = (config.db as string | undefined) ?? "restheart";
        const collection = (config.collection as string | undefined) ?? "users";

        const users = mongo.db(db).collection(collection)
            .find(filter, { limit, skip, sort: { _id: -1 }, projection: { password: 0 } })
            .toArray();

        res.json(users);
    },
});
