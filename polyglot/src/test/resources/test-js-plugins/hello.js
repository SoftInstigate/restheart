const BsonUtils = Java.type("org.restheart.utils.BsonUtils");
const HttpString = Java.type("io.undertow.util.HttpString");

export const options = {
    name: "helloWorldService",
    description: "just another Hello World",
    uri: "/hello",
    secured: false, // optional, default false
    matchPolicy: "EXACT" // optional, default PREFIX
}

export function handle(req, res) {
    LOGGER.debug('request content {}', req.getContent());
    // just an example of how to use BsonUtils
    exampleBsonUtils();

    if (req.isGet()) {
        const rc = JSON.parse(req.getContent() || '{}');

        let body = {
            msg: `Hello ${rc.name || 'World'}`
        }

        res.setContent(JSON.stringify(body));
        res.setContentTypeAsJson();

    } else if (req.isOptions()) {
        res.getHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"),
                    "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                    "Accept, Accept-Encoding, Authorization, "
                    + "Content-Length, Content-Type, Host, "
                    + "If-Match, Origin, X-Requested-With, "
                    + "User-Agent, No-Auth-Challenge");
    } else {
        res.setStatusCode(405); // method not allowed
    }
}

/**
 * this snippet shows how to use BsonUtils.document() and BsonUtils.array()
 */
function exampleBsonUtils() {
    // _array is a Java BsonArray with two BsonDocument elements
    const _array = BsonUtils.array().add(
        BsonUtils.document().put("foo", 1).put("bar", 2),
        BsonUtils.document().put("foo", 1).put("bar", 2));
    // turn the java object to a javascript object
    // parsing the string representation of _array (got via .toJson())
    const array = JSON.parse(_array.toJson());
    // log it
    LOGGER.info('Example BSON array built with BsonUtils {}', array);
}