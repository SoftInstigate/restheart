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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The request body of a non-blocking service (#799, #803).
 *
 * <p>Bodies up to {@code non-blocking-content-limit} (256 KiB in the integration tests,
 * below their {@code MAX_ENTITY_SIZE} of 1 MiB) are read by the core and attached as
 * {@code BufferedContent}. Larger ones are left in the request,
 * as before 9.15.0, and the service reads them whole by itself: with a Content-Length
 * nothing is read in advance, and a chunked body that exceeds the limit is put back.
 * The service is {@code NonBlockingBodyService} in restheart-test-plugins.</p>
 */
public class NonBlockingBodyIT extends AbstactIT {
    private static final URI SERVICE = URI.create("http://localhost:8080/test-non-blocking-body");
    private static final int SMALL = 100 * 1024;
    private static final int LARGE = 512 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void smallBodyWithContentLengthIsBuffered() throws Exception {
        assertReport(post(body(SMALL), false), true, body(SMALL));
    }

    @Test
    public void smallChunkedBodyIsBuffered() throws Exception {
        assertReport(post(body(SMALL), true), true, body(SMALL));
    }

    @Test
    public void largeBodyWithContentLengthReachesTheService() throws Exception {
        assertReport(post(body(LARGE), false), false, body(LARGE));
    }

    @Test
    public void largeChunkedBodyReachesTheServiceWhole() throws Exception {
        assertReport(post(body(LARGE), true), false, body(LARGE));
    }

    private HttpResponse<String> post(byte[] body, boolean chunked) throws Exception {
        // ofInputStream has no known length, so the client sends it chunked
        var publisher = chunked
                ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))
                : HttpRequest.BodyPublishers.ofByteArray(body);

        var request = HttpRequest.newBuilder(SERVICE)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/octet-stream")
                .POST(publisher)
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertReport(HttpResponse<String> response, boolean buffered, byte[] body) throws Exception {
        assertEquals(200, response.statusCode(), response.body());

        JsonObject report = JsonParser.parseString(response.body()).getAsJsonObject();

        assertEquals(buffered, report.get("buffered").getAsBoolean(), response.body());
        assertEquals(body.length, report.get("size").getAsInt(), response.body());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)),
                report.get("sha256").getAsString(), "the body reached the service altered");
    }

    private static byte[] body(int size) {
        var ret = new byte[size];
        new Random(size).nextBytes(ret);
        return ret;
    }
}
