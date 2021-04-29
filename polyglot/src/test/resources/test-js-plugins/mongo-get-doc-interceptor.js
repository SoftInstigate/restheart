export const options = {
    name: "mongoGetDocInteceptor",
    description: "a js interceptor that modified the response of GET /coll/<docid>",
    secured: false, // optional, default false
    matchPolicy: "EXACT", // optional, default PREFIX
    pluginClass: "MongoInterceptor"
}

export function handle(request, response) {
    const BsonUtils = Java.type("org.restheart.utils.BsonUtils");
    var bson = response.getContent();

    bson.asDocument().put("injectedDoc", BsonUtils.parse("{ 'n': 1, 's': 'foo' }"));
}

export function resolve(request) {
    return request.isGet() && request.isDocument() && "coll" === request.getCollectionName();
}