const BsonUtils = Java.type("org.restheart.utils.BsonUtils");

export const options = {
    name: "helloWorldService",
    description: "just another Hello World",
    uri: "/hello",
    secured: false, // optional, default false
    matchPolicy: "EXACT" // optional, default PREFIX
}

export function handle(request, response) {
    exampleBsonUtils();

    LOGGER.debug('request {}', request.getContent());
    const rc = JSON.parse(request.getContent() || '{}');

    let body = {
        msg: `Hello ${rc.name || 'World'}`
    }

    response.setContent(JSON.stringify(body));
    response.setContentTypeAsJson();
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