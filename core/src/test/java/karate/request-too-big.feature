# A request body over MAX_ENTITY_SIZE or MULTIPART_MAX_ENTITY_SIZE (1 MiB in conf-overrides.yml)
# is a client error: 413, with a message naming the limit, never a 500.

@request-too-big
Feature: a request body over the maximum size is answered with 413

  Background:
    * url baseUrl
    * def authHeader = adminAuth
    * def db = '/test-request-too-big'
    * def coll = db + '/coll'
    * def tooBigMessage = 'request exceeds the maximum size of 1048576 bytes'
    # 2 MiB, twice the limit
    * def bigString = 'a'.repeat(2 * 1024 * 1024)
    * def bigJson = '{"a":"' + bigString + '"}'
    # the oversized requests go through java.net.http: karate's Apache client writes the whole body
    # before reading the response, so it fails with "Broken pipe" when RESTHeart answers and closes
    # the connection early. With chunked = true the body has an unknown length and is sent with
    # Transfer-Encoding: chunked, otherwise with Content-Length.
    * def post =
      """
      function(url, auth, contentType, body, chunked) {
        var HttpClient = Java.type('java.net.http.HttpClient');
        var Version = Java.type('java.net.http.HttpClient$Version');
        var HttpRequest = Java.type('java.net.http.HttpRequest');
        var BodyPublishers = Java.type('java.net.http.HttpRequest$BodyPublishers');
        var BodyHandlers = Java.type('java.net.http.HttpResponse$BodyHandlers');
        var URI = Java.type('java.net.URI');
        var JString = Java.type('java.lang.String');

        var bytes = new JString(body).getBytes('UTF-8');
        // fromPublisher() without a length hides the length of the wrapped publisher, so the body is chunked;
        // no JS callback here: the client runs it on its own threads, where GraalJS refuses to run
        var publisher = chunked
          ? BodyPublishers.fromPublisher(BodyPublishers.ofByteArray(bytes))
          : BodyPublishers.ofByteArray(bytes);
        var client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
        var request = HttpRequest.newBuilder(URI.create(url))
          .header('Authorization', auth)
          .header('Content-Type', contentType)
          .POST(publisher)
          .build();

        try {
          var response = client.send(request, BodyHandlers.ofString());
          return { status: response.statusCode(), body: '' + response.body() };
        } catch (e) {
          // no response at all, e.g. the connection was reset before the response was read
          return { status: -1, body: '' + e };
        }
      }
      """
    * def boundary = 'request-too-big-boundary'
    * def bigMultipart = '--' + boundary + '\r\nContent-Disposition: form-data; name="big"\r\n\r\n' + bigString + '\r\n--' + boundary + '--\r\n'

  Scenario: setup
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: a JSON body over the limit, with Content-Length, is answered with 413
    * def result = post(baseUrl + coll, authHeader, 'application/json', bigJson, false)
    * karate.log('response:', result)
    * match result.status == 413
    * match JSON.parse(result.body).message == tooBigMessage

  # ignored: Undertow 2.4.4 closes the connection when a chunked body exceeds the limit
  # (ChunkedStreamSourceConduit closes it on any read error), so no response reaches the client
  @ignore
  Scenario: a JSON body over the limit, chunked, is answered with 413
    * def result = post(baseUrl + coll, authHeader, 'application/json', bigJson, true)
    * karate.log('response:', result)
    * match result.status == 413
    * match JSON.parse(result.body).message == tooBigMessage

  Scenario: a multipart body over the limit is answered with 413
    * def result = post(baseUrl + coll, authHeader, 'multipart/form-data; boundary=' + boundary, bigMultipart, false)
    * karate.log('response:', result)
    * match result.status == 413
    * match JSON.parse(result.body).message == tooBigMessage

  Scenario: a JSON body under the limit is still accepted
    * header Authorization = authHeader
    Given path coll
    And request { "a": 1 }
    When method POST
    Then status 201
