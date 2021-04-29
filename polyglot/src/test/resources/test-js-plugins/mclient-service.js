export const options = {
    name: "mclientService",
    description: "just an example JavaScript service that uses the MongoClient",
    uri: '/mclientService',
    secured: true, // optional, default false
    matchPolicy: "EXACT" // optional, default PREFIX
}

export function handle(request, response) {
    // pluginArgs comes from configuration file plugins-args.jsMClient
    LOGGER.debug("pluginArgs {}", pluginArgs);

    const limit = parseInt(request.getQueryParameterOfDefault("limit", "100"));
    const skip = parseInt(request.getQueryParameterOfDefault("skip", "0"));

    if (isNaN(skip)) {
        response.setInError(400, 'wrong skip qparam');
        return;
    }

    if (isNaN(limit)) {
        response.setInError(400, 'wrong limit qparam');
        return;
    }

    const _filter = request.getQueryParameterOfDefault("filter", "{}");

    const BsonUtils = Java.type("org.restheart.utils.BsonUtils");

    let filter;

    try {
        filter = BsonUtils.parse(_filter);
    } catch(e) {
        response.setInError(400, 'wrong filter qparam: ' + e);
        return;
    }

    const BsonDocument = Java.type("org.bson.BsonDocument");
    // mclient is the Java mongodb driver => find() expects a Java BsonDocument
    let it = mclient.getDatabase("restheart").getCollection("coll", BsonDocument.class).find(filter).limit(limit).skip(skip).iterator();

    const BsonArray = Java.type("org.bson.BsonArray");
    let results = new BsonArray();

    while(it.hasNext()) {
        results.add(it.next());
    }

    response.setContent(BsonUtils.toJson(results));
    response.setContentTypeAsJson();
}
