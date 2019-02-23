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
package io.uiam.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.uiam.utils.BuffersUtils;
import io.uiam.utils.HttpStatus;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Response extends AbstractExchange {

    private static final AttachmentKey<Boolean> IN_ERROR_KEY
            = AttachmentKey.create(Boolean.class);
    private static final AttachmentKey<Integer> STATUS_CODE_KEY
            = AttachmentKey.create(Integer.class);
    private static final AttachmentKey<String> CONTENT_TYPE_KEY
            = AttachmentKey.create(String.class);

    public Response(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(Request.class);
    }

    public static Response wrap(HttpServerExchange exchange) {
        return new Response(exchange);
    }

    /**
     * @return the responseContentType
     */
    public String getContentType() {
        return getWrapped().getAttachment(CONTENT_TYPE_KEY);
    }

    /**
     * @param the response content to set
     */
    public void setContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        getWrapped().putAttachment(CONTENT_AS_JSON, content);
        syncBufferedContent(wrapped);
    }

    /**
     * @return the responseContentType
     */
    public String getContentType(String responseContentType) {
        return getWrapped().getAttachment(CONTENT_TYPE_KEY);
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getWrapped().putAttachment(CONTENT_TYPE_KEY, responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        getWrapped().putAttachment(CONTENT_TYPE_KEY, "application/json");
    }

    /**
     * @return the responseStatusCode
     */
    public int getStatusCode() {
        if (getWrapped().getAttachment(STATUS_CODE_KEY) == null) {
            return getWrapped().getStatusCode();
        } else {
            return getWrapped().getAttachment(STATUS_CODE_KEY);
        }
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setStatusCode(int responseStatusCode) {
        getWrapped().putAttachment(STATUS_CODE_KEY, responseStatusCode);
    }

    /**
     * the Json is saved in the exchange. if modified syncBufferedContent() must
     * be invoked
     *
     * @return the request body as Json
     * @throws java.io.IOException
     * @throws com.google.gson.JsonSyntaxException
     */
    public JsonElement getContentAsJson()
            throws IOException, JsonSyntaxException {
        if (getWrapped().getAttachment(CONTENT_AS_JSON) == null) {
            getWrapped().putAttachment(
                    CONTENT_AS_JSON,
                    PARSER.parse(
                            BuffersUtils.toString(getContent(),
                                    Charset.forName("utf-8")))
            );
        }
        return getWrapped().getAttachment(CONTENT_AS_JSON);
    }

    /**
     * @return the inError
     */
    public boolean isInError() {
        return getWrapped().getAttachment(IN_ERROR_KEY);
    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        getWrapped().putAttachment(IN_ERROR_KEY, inError);
    }

    /**
     *
     * @param code
     * @param body
     */
    public void endExchange(int code, JsonObject body) throws IOException {
        setStatusCode(code);
        setInError(true);
        setContent(body);
    }

    /**
     *
     * @param code
     * @param message
     */
    public void endExchangeWithMessage(int code, String message) {
            endExchangeWithMessage(code, message, null);
    }

    /**
     *
     * @param code
     * @param message
     * @param t
     */
    public void endExchangeWithMessage(int code, String message, Throwable t) {
        setStatusCode(code);
        setContentTypeAsJson();
        setInError(true);
        try {
            setContent(getErrorObject(code,
                    HttpStatus.getStatusText(code),
                    message,
                    t,
                    false));
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    protected AttachmentKey<PooledByteBuffer[]> getBufferedDataKey() {
        return ModificableContentSinkConduit.BUFFERED_RESPONSE_DATA;
    };

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

    private static JsonObject getErrorObject(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) {
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
}
