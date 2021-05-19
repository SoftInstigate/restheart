const BsonUtils = Java.type('org.restheart.utils.BsonUtils');

export const options = {
    name: "mongoPostCollInterceptor",
    description: "modifies the content of POST requests adding a timestamp",
    interceptPoint: "REQUEST_AFTER_AUTH",
    pluginClass: "MongoInterceptor"
}

export function handle(request, response) {
    request.getContent().putAll(BsonUtils.parse(`{ "timestamp": new Date() }`));
}

export function resolve(request) {
    return request.isPost() && request.getCollectionName() === "coll";
}