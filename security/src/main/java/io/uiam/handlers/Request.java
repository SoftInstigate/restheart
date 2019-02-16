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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.undertow.connector.PooledByteBuffer;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ExchangeHelper {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ExchangeHelper.class);

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";

    public static int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16byte

    private final HttpServerExchange wrapped;

    private static final JsonParser PARSER = new JsonParser();

    private static final AttachmentKey<JsonElement> REQUEST_CONTENT_AS_JSON_KEY = AttachmentKey.create(JsonElement.class);

    private static final AttachmentKey<Boolean> IN_ERROR_KEY = AttachmentKey.create(Boolean.class);
    private static final AttachmentKey<Long> REQUEST_START_TIME_KEY = AttachmentKey.create(Long.class);

    private static final AttachmentKey<Integer> RESPONSE_STATUS_CODE_KEY = AttachmentKey.create(Integer.class);

    private static final AttachmentKey<String> RESPONSE_CONTENT_TYPE_KEY = AttachmentKey.create(String.class);
    private static final AttachmentKey<String> RESPONSE_CONTENT_KEY = AttachmentKey.create(String.class);
    private static final AttachmentKey<JsonElement> RESPONSE_CONTENT_AS_JSON_KEY = AttachmentKey.create(JsonElement.class);

    public ExchangeHelper(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    private static METHOD selectRequestMethod(HttpString _method) {
        if (Methods.GET.equals(_method)) {
            return METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            return METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            return METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            return METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            return METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            return METHOD.OPTIONS;
        } else {
            return METHOD.OTHER;
        }
    }

    /**
     *
     * @return the request method
     */
    public METHOD getMethod() {
        return selectRequestMethod(wrapped.getRequestMethod());
    }

    /**
     * @return the request body as String
     */
    public byte[] getRequestBodyAsBytes() throws IOException {
        PooledByteBuffer[] buffers = getBufferedRequestDataAttachment(wrapped);
        
        ByteBuffer content
                = readByteBuffer(
                        getBufferedRequestDataAttachment(wrapped));

        if (content != null) {
            LOGGER.debug("BUFFERED_REQUEST_DATA is {} bytes", content.limit());
        } else {
            LOGGER.debug("BUFFERED_REQUEST_DATA is {}", content);
        }

        return content == null ? null : content.array();
    }

    /**
     * @return the request body as String
     */
    public String getRequestBodyAsText() throws IOException {
        String content = readString(
                readByteBuffer(
                        getBufferedRequestDataAttachment(wrapped)));

        if (content != null && content.length() > 100) {
            LOGGER.debug("BUFFERED_REQUEST_DATA is {} (content truncated)",
                    content.substring(100));
        } else {
            LOGGER.debug("BUFFERED_REQUEST_DATA is {}", content);
        }

        return content;
    }

    private PooledByteBuffer[] getBufferedRequestDataAttachment(
            final HttpServerExchange exchange) {
        Field f;

        try {
            f = HttpServerExchange.class.getDeclaredField("BUFFERED_REQUEST_DATA");
            f.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException("could not find BUFFERED_REQUEST_DATA field", ex);
        }

        try {
            return exchange.getAttachment(
                    (AttachmentKey<PooledByteBuffer[]>) f.get(exchange));
        }
        catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
        }
    }

    /**
     * @return the request body as JsonElement
     */
    public JsonElement getRequestBodyAsJson()
            throws IOException, JsonSyntaxException {
        if (wrapped.getAttachment(REQUEST_CONTENT_AS_JSON_KEY) == null) {
            wrapped.putAttachment(REQUEST_CONTENT_AS_JSON_KEY,
                    PARSER.parse(getRequestBodyAsText()));
        }

        return wrapped.getAttachment(REQUEST_CONTENT_AS_JSON_KEY);
    }

    /**
     * @return the requestStartTime
     */
    public Long getRequestStartTime() {
        return wrapped.getAttachment(REQUEST_START_TIME_KEY);
    }

    /**
     * @param requestStartTime the requestStartTime to set
     */
    public void setRequestStartTime(Long requestStartTime) {
        wrapped.putAttachment(REQUEST_START_TIME_KEY, requestStartTime);
    }

    /**
     * @return the responseStatusCode
     */
    public int getResponseStatusCode() {
        if (wrapped.getAttachment(RESPONSE_STATUS_CODE_KEY) == null) {
            return wrapped.getStatusCode();
        } else {
            return wrapped.getAttachment(RESPONSE_STATUS_CODE_KEY);
        }
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setResponseStatusCode(int responseStatusCode) {
        wrapped.putAttachment(RESPONSE_STATUS_CODE_KEY, responseStatusCode);
    }

    /**
     * @return the response content as String
     */
    public String getResponseContent() {
        return wrapped.getAttachment(RESPONSE_CONTENT_KEY);
    }

    /**
     * can be null if the content is not a String
     *
     * @param content the response content to set
     */
    public void setResponseContent(String content) {
        wrapped.putAttachment(RESPONSE_CONTENT_KEY, content);
    }

    /**
     * can be null if the content is not Json
     *
     * @return the response body as JsonElement
     */
    public JsonElement getResponseContentAsJson() {
        return wrapped.getAttachment(RESPONSE_CONTENT_AS_JSON_KEY);
    }

    /**
     * @param the response content to set
     */
    public void setResponseContent(JsonElement content) {
        wrapped.putAttachment(RESPONSE_CONTENT_AS_JSON_KEY, content);
    }

    /**
     * @return the responseContentType
     */
    public String getResponseContentType() {
        return wrapped.getAttachment(RESPONSE_CONTENT_TYPE_KEY);
    }

    /**
     * @return the responseContentType
     */
    public String getRequestContentType() {
        return wrapped.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setResponseContentType(String responseContentType) {
        wrapped.putAttachment(RESPONSE_CONTENT_TYPE_KEY, responseContentType);
    }

    /**
     */
    public void setResponseContentTypeAsJson() {
        wrapped.putAttachment(RESPONSE_CONTENT_TYPE_KEY, "application/json");
    }

    /**
     * @return the inError
     */
    public boolean isInError() {
        return wrapped.getAttachment(IN_ERROR_KEY);
    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        wrapped.putAttachment(IN_ERROR_KEY, inError);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return this.wrapped.getSecurityContext().getAuthenticatedAccount();
    }

    public enum METHOD {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, OTHER
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }

    /**
     * helper method to check if the request content is Json
     *
     * @return true if Content-Type request header is application/json
     */
    public boolean isRequesteContentTypeJson() {
        return "application/json".equals(getRequestContentType());
    }

    /**
     * helper method to check if the request content is Xm
     *
     * @return true if Content-Type request header is application/xml or text/xml
     */
    public boolean isRequesteContentTypeXml() {
        return "text/xml".equals(getRequestContentType())
                || "application/xml".equals(getRequestContentType());
    }
    
    /**
     * helper method to check if the request content is text
     *
     * @return true if Content-Type request header starts with text/
     */
    public boolean isRequesteContentTypeText() {
        return getRequestContentType() != null &&
                getRequestContentType().startsWith("text/");
    }

    public String readString(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }

        return StandardCharsets.UTF_8
                .decode(buffer)
                .toString();
    }

    /**
     * TODO throw exception if not enough space
     *
     * @param srcs
     * @return
     * @throws IOException
     */
    public ByteBuffer readByteBuffer(final PooledByteBuffer[] srcs)
            throws IOException {
        if (srcs == null) {
            return null;
        }

        ByteBuffer dst = ByteBuffer.allocate(MAX_CONTENT_SIZE);

        int copied = 0;
        for (int i = 0; i < srcs.length; ++i) {
            PooledByteBuffer pooled = srcs[i];
            if (pooled != null) {
                final ByteBuffer buf = pooled.getBuffer();

                if (buf.remaining() > dst.remaining()) {
                    LOGGER.error("Request content too big. Max size is {} bytes",
                            MAX_CONTENT_SIZE);
                    throw new IOException("Request content too big");
                }

                if (buf.hasRemaining()) {
                    copied += Buffers.copy(dst, buf);

                    // very important, I lost a day for this!
                    buf.rewind();
                }
            }
        }

        return dst.position(0).limit(copied > 0 ? copied : 0);
    }
}
