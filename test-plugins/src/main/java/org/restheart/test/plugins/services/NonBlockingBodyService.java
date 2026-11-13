/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.test.plugins.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.restheart.exchange.BufferedContent;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * A non-blocking service that reports how it got the request body: buffered by the
 * core, or read by itself with Undertow's asynchronous receiver, as non-blocking
 * services did before 9.15.0.
 *
 * <p>Responds {@code {"buffered": <bool>, "size": <bytes>, "sha256": "<hex>"}}. The
 * integration tests check that a body larger than {@code non-blocking-content-limit}
 * reaches the service whole (#803).</p>
 */
@RegisterPlugin(
        name = "nonBlockingBody",
        description = "reports how a non-blocking service gets the request body",
        enabledByDefault = true,
        blocking = false,
        defaultURI = "/test-non-blocking-body")
public class NonBlockingBodyService implements ByteArrayService {
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        final var exchange = request.getExchange();
        final var buffered = BufferedContent.get(exchange);

        if (buffered != null) {
            response.setContentTypeAsJson();
            response.setContent(report(true, buffered).getBytes(StandardCharsets.UTF_8));
            return;
        }

        // not buffered by the core: read it here, without blocking the IO thread
        response.setCustomSender(() -> exchange.getRequestReceiver()
                .receiveFullBytes((ex, body) -> send(ex, report(false, body))));
    }

    private static void send(HttpServerExchange exchange, String json) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(json);
    }

    private static String report(boolean buffered, byte[] body) {
        return "{\"buffered\": " + buffered + ", \"size\": " + body.length + ", \"sha256\": \"" + sha256(body) + "\"}";
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
