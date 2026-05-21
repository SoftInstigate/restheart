# ALLOW_UNESCAPED_CHARACTERS_IN_URL does not cover '[' and ']' in query string in 2.4.0 (regression from 2.3.x)

## Summary

In Undertow 2.4.0, the new `RequestParser` rejects literal `[` and `]` characters in
HTTP query strings with HTTP 400 Bad Request, **even when
`UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL` is set to `true`**.

This is a regression: Undertow 2.3.x accepted the same requests without error.

## Reproducer

```java
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.util.StatusCodes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BracketQueryReproducer {
    public static void main(String[] args) throws Exception {
        var server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true)
                .setHandler(exchange -> {
                    exchange.setStatusCode(StatusCodes.OK);
                    exchange.endExchange();
                })
                .build();

        server.start();

        // This request contains '[' and ']' in the query string
        var client = HttpClient.newHttpClient();
        var response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/coll?filter={'_id':{'$in':['doc2']}}"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Undertow 2.3.x → 200; Undertow 2.4.0 → 400
        System.out.println("Status: " + response.statusCode());

        server.stop();
    }
}
```

**Expected:** `Status: 200`  
**Actual (2.4.0):** `Status: 400`

## Root cause analysis

In `RequestParser.parseQueryParameters()` (which replaced `HttpRequestParser` in 2.4.0),
the character dispatch is:

1. `isPathSegmentChar(c)` OR c is `/` or `?` → accept
2. c is space → end of query
3. else:
   - if `!allowUnescapedCharactersInUrl` → throw `BadRequestException`
   - **if `isRequestTargetChar(c)` is `true` → throw `BadRequestException`** ← `[` and `]` hit this branch
   - else → accept with `urlDecodeRequired = true`

`isRequestTargetChar` returns `true` for `[` (0x5b) and `]` (0x5d) because they are
present in the `REQUEST_TARGET` bitmask but not in the `PATH_SEGMENT` bitmask.
The effect: `[` and `]` are **always** rejected regardless of the
`ALLOW_UNESCAPED_CHARACTERS_IN_URL` flag, because that flag only guards the final
`else` branch.

Characters like `{` and `}` (not in either bitmask) are correctly accepted when the
flag is `true`, making the behaviour inconsistent.

## Expected behaviour

When `ALLOW_UNESCAPED_CHARACTERS_IN_URL=true`, `[` and `]` should be accepted in query
strings as in 2.3.x — or the flag name and docs should clearly enumerate which characters
remain excluded so integrators can adapt.

## Versions tested

| Version      | Result        |
|--------------|---------------|
| 2.3.23.Final | ✓ 200 OK      |
| 2.4.0.Final  | ✗ 400 Bad Request |
