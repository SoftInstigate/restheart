/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.restheart.utils.BuffersUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonProxyResponse extends ProxyResponse<JsonElement> {
    static {
        LOGGER = LoggerFactory.getLogger(JsonProxyRequest.class);
    }

    protected JsonProxyResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    public static JsonProxyResponse of(HttpServerExchange exchange) {
        return new JsonProxyResponse(exchange);
    }

    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public JsonElement readContent() throws IOException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrappedExchange().getAttachment(getRawContentKey()) == null) {
            return JsonNull.INSTANCE;
        } else {
            try {
                return JsonParser.parseString(BuffersUtils.toString(getBuffer(), StandardCharsets.UTF_8));
            } catch (JsonParseException ex) {
                throw new IOException("Error parsing json", ex);
            }
        }
    }

    /**
     * writes the response content
     *
     * allocates the PooledByteBuffer array so close() must be invoked
     * to avoid memory leacks
     */
    @Override
    public void writeContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        if (content == null) {
            setBuffer(null);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getBuffer();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setBuffer(dest);
            }

            BuffersUtils.transfer(ByteBuffer.wrap(content.toString().getBytes()), dest, wrapped);
        }
    }

    @Override
    protected JsonElement getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException {
        var resp = new JsonObject();
        resp.add("http status code", new JsonPrimitive(code));
        resp.add("http status description", new JsonPrimitive(httpStatusText));
        if (message != null) {
            resp.add("message", new JsonPrimitive(avoidEscapedChars(message)));
        }
        var nrep = new JsonObject();
        if (t != null) {
            nrep.add("class", new JsonPrimitive(t.getClass().getName()));
            if (includeStackTrace) {
                JsonArray stackTrace = getStackTrace(t);
                if (stackTrace != null) {
                    nrep.add("stack trace", stackTrace);
                }
            }
            resp.add("exception", nrep);
        }
        return resp;
    }

    private static String avoidEscapedChars(String s) {
        return s == null ? null : s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    private static JsonArray getStackTrace(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }

        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        var st = avoidEscapedChars(sw.toString());
        var lines = st.split("\n");
        var list = new JsonArray();
        for (String line : lines) {
            list.add(new JsonPrimitive(line));
        }
        return list;
    }
}
