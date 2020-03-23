/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.handlers.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import static org.restheart.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.utils.BuffersUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayResponse extends ProxableResponse<byte[]> {

    protected ByteArrayResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(JsonRequest.class);
    }

    public static ByteArrayResponse wrap(HttpServerExchange exchange) {
        return new ByteArrayResponse(exchange);
    }

    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public byte[] readContent()
            throws IOException {
        return BuffersUtils.toByteArray(getRawContent());
    }

    @Override
    public void writeContent(byte[] content) throws IOException {
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
                    ByteBuffer.wrap(content),
                    dest,
                    wrapped);
        }
    }

    @Override
    protected byte[] getErrorContent(int code,
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
        return resp.toString().getBytes();
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
