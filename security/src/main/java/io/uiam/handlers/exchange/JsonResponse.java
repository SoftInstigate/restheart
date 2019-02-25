/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.handlers.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.uiam.utils.BuffersUtils;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonResponse extends Response<JsonElement> {

    protected static final JsonParser PARSER = new JsonParser();

    private JsonResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(JsonRequest.class);
    }

    public static JsonResponse wrap(HttpServerExchange exchange) {
        return new JsonResponse(exchange);
    }

    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public JsonElement readContent()
            throws IOException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrapped().getAttachment(getRawContentKey()) == null) {
            return JsonNull.INSTANCE;
        } else {
            try {
                LOGGER.debug("************************ raw content befor parsing");
                var raw = getRawContent();
                var cont = 0;
                for (PooledByteBuffer dest : raw) {
                    if (dest != null) {
                        LOGGER.debug("processing buffer {}", cont);
                        ByteBuffer src = dest.getBuffer();
                        StringBuilder sb = new StringBuilder();

                        if (LOGGER.isDebugEnabled()) {
                            Buffers.dump(src, sb, 2, 2);
                            LOGGER.debug("\n{}", sb);
                        }
                    }
                }
                LOGGER.debug("************************ raw content befor parsing");

                String rawContentAsString = BuffersUtils.toString(getRawContent(),
                        StandardCharsets.UTF_8);
                
                return PARSER.parse(rawContentAsString);
            }
            catch (JsonParseException ex) {
                throw new IOException("Error parsing json", ex);
            }
        }
    }

    @Override
    public void writeContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        if (content == null) {
            setRawContent(null);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getRawContent();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setRawContent(dest);
            }

            BuffersUtils.transfer(
                    ByteBuffer.wrap(content.toString().getBytes()),
                    dest,
                    wrapped);
        }
    }

    @Override
    protected JsonElement getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException {
        JsonObject resp = new JsonObject();
        resp.add("http status code", new JsonPrimitive(code));
        resp.add("http status description", new JsonPrimitive(httpStatusText));
        if (message != null) {
            resp.add("message", new JsonPrimitive(avoidEscapedChars(message)));
        }
        JsonObject nrep = new JsonObject();
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
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String st = sw.toString();
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");
        JsonArray list = new JsonArray();
        for (String line : lines) {
            list.add(new JsonPrimitive(line));
        }
        return list;
    }
}
