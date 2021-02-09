({
    options: {
        name: "mongoCollInterceptor",
        description: "modifies the response of GET /coll/<docid>",
        interceptPoint: "RESPONSE",
        pluginClass: "MongoInterceptor"
    },

    handle: (request, response) => {
        const BsonUtils = Java.type("org.restheart.utils.BsonUtils");
        var bson = response.getContent();

        bson.asDocument().put("injectedDoc", BsonUtils.parse("{ 'n': 1, 's': 'foo' }"));
    },

    resolve: (request) => {
        return request.isGet() && request.isDocument() && "coll" === request.getCollectionName();
    }
})