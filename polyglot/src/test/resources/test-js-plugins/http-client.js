const HttpClient = Java.type('java.net.http.HttpClient');
const HttpVersion = Java.type('java.net.http.HttpClient.Version')
const HttpRequest = Java.type('java.net.http.HttpRequest');
const BodyPublishers = Java.type('java.net.http.HttpRequest.BodyPublishers');
const BodyHandlers = Java.type('java.net.http.HttpResponse.BodyHandlers')
const URI = Java.type('java.net.URI');
const Duration = Java.type('java.time.Duration');
const JsonParser = Java.type('com.google.gson.JsonParser');

export const options = {
    name: "httpClientService",
    description: "a service that uses java.net.http.HttpClient to execute a GET request",
    uri: "/httpClient",
    secured: false, // optional, default false
    matchPolicy: "EXACT" // optional, default PREFIX
}

export function handle(request, response) {
    // use Java HttpClient to execute a POST request to httpbin
    const httpClient = HttpClient.newBuilder()
            .version(HttpVersion.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    const uri = URI.create("https://httpbin.org/anything");

    const getRequest = HttpRequest.newBuilder()
        .POST(BodyPublishers.ofString(`{"foo": 2, "bar": 1, "from": "${uri}"}`))
        .uri(uri)
        .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
        .build();

    const responseBody = httpClient.send(getRequest, BodyHandlers.ofString()).body();

    // responseBody is a Java String
    LOGGER.info("raw response body {}", responseBody);

    // using the Gson Java API to parse the String response to a JsonObject...
    //// 1) parse the response with JsonParser and get it as a Gson JsonElement
    const responseGson = JsonParser.parseString(responseBody);

    //// 2) turn the JsonElement to a JsonObject
    const responseGsonObject = responseGson.getAsJsonObject();

    // get the data property in JsonObject
    const json = responseGsonObject.get("json");

    response.setContent(json.toString());
    response.setContentTypeAsJson();
}