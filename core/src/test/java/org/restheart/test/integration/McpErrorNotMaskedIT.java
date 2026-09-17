/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * A {@code /mcp} request that ends in an error keeps its status; the catalog filter does not turn
 * it into a 500.
 *
 * <p>{@code mcpCatalogFilterInterceptor} runs on every {@code /mcp} response to strip
 * {@code resources/list} of what the caller cannot read. It used to decide a response was none of
 * its business only from the {@code isInError} flag, then parse the body as JSON unconditionally.
 * The transport ends several requests with an error <em>status</em> and a plain-text body without
 * raising that flag — a bad {@code Accept} on {@code initialize} is the simplest — so the filter
 * accepted one, failed to parse it, and the request came back {@code 500} instead of the
 * {@code 400} the transport had set. That masks a client's own mistake as a server fault.
 *
 * <p>This pins the fix: a bad {@code Accept} on {@code initialize} answers {@code 400}, and never
 * {@code 500}.
 */
public class McpErrorNotMaskedIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",\
            "capabilities":{},"clientInfo":{"name":"probe","version":"0"}}}""";

    @Test
    public void badAcceptOnInitialize_is400_not500() throws Exception {
        // Accept omits text/event-stream, which the transport requires on initialize. It answers
        // 400 with a plain-text body; the filter must let that through untouched.
        var response = post("application/json");

        assertEquals(400, response.statusCode(),
                "a bad Accept must be the client's 400, not a 500 from the catalog filter: " + response.body());
        assertFalse(response.body().contains("InterceptorException"),
                "the catalog filter turned a 400 into a 500: " + response.body());
    }

    @Test
    public void goodAcceptOnInitialize_stillSucceeds() throws Exception {
        // the control: with both media types the same request initializes, so the stricter filter
        // has not started dropping valid responses
        var response = post("application/json, text/event-stream");

        assertEquals(200, response.statusCode(), "initialize with a correct Accept must still work: " + response.body());
    }

    private static HttpResponse<String> post(String accept) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/mcp"))
                .header("Authorization", ADMIN_BASIC)
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
