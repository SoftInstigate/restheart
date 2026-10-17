/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.InProcessDispatcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * PROOF OF CONCEPT: re-dispatches a request through this same RESTHeart, in-process.
 *
 * <p>
 * {@code <METHOD> /in-process-poc?path=/ping[&method=GET]} sends {@code method} (default: the
 * incoming method) to {@code path} through the full handler chain, over an XNIO pipe, with the
 * incoming {@code Host}, {@code Authorization} and {@code Content-Type} headers and the incoming
 * body, and answers with the status, headers and body that came back. For example:
 * </p>
 *
 * <pre>
 * curl -u admin:secret 'localhost:8080/in-process-poc?path=/ping'
 * curl -u admin:secret -X POST -H 'Content-Type: application/json' -d '{"title":"x"}' 'localhost:8080/in-process-poc?path=/todos'
 * </pre>
 */
@RegisterPlugin(
        name = "inProcessDispatchPoc",
        description = "PROOF OF CONCEPT: re-dispatches a request through this server in-process, over an XNIO pipe",
        enabledByDefault = false,
        secure = false,
        defaultURI = "/in-process-poc")
public class InProcessDispatchPocService implements ByteArrayService {
    @Inject("in-process-dispatcher")
    private InProcessDispatcher dispatcher;

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        var path = request.getQueryParameterOrDefault("path", "/ping");
        var method = HttpString.tryFromString(request.getQueryParameterOrDefault("method", request.getMethod().name()));

        var headers = new HeaderMap();
        copy(request.getHeaders(), headers, Headers.HOST);
        copy(request.getHeaders(), headers, Headers.AUTHORIZATION);
        copy(request.getHeaders(), headers, Headers.CONTENT_TYPE);

        var started = System.nanoTime();
        var dispatched = dispatcher.dispatch(method, path, headers, request.getContent());
        var elapsedMicros = (System.nanoTime() - started) / 1_000;

        var out = new JsonObject();
        out.addProperty("status", dispatched.status());
        out.addProperty("elapsedMicros", elapsedMicros);
        out.addProperty("openLanes", dispatcher.openLanes());
        out.addProperty("idleLanes", dispatcher.idleLanes());

        var responseHeaders = new JsonObject();
        dispatched.headers().forEach(h -> responseHeaders.addProperty(h.getHeaderName().toString(), h.getFirst()));
        out.add("headers", responseHeaders);

        var body = new String(dispatched.body(), StandardCharsets.UTF_8);
        try {
            out.add("body", JsonParser.parseString(body));
        } catch (JsonSyntaxException jse) {
            out.addProperty("body", body);
        }

        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);
        response.setContent(out.toString());
    }

    private static void copy(HeaderMap from, HeaderMap to, HttpString name) {
        var value = from.getFirst(name);
        if (value != null) {
            to.put(name, value);
        }
    }
}
