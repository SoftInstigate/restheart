/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers.exchange;

import org.restheart.security.utils.HttpStatus;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class Response<T> extends AbstractExchange<T> {

    private static final AttachmentKey<Boolean> IN_ERROR_KEY
            = AttachmentKey.create(Boolean.class);
    private static final AttachmentKey<Integer> STATUS_CODE
            = AttachmentKey.create(Integer.class);
    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA
            = AttachmentKey.create(PooledByteBuffer[].class);
    
    protected Response(HttpServerExchange exchange) {
        super(exchange);
    }
    
    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getResponseHeaders()
                .getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * @return the responseContentType
     */
    @Override
    public String getContentType() {
        if (getWrapped().getResponseHeaders().get(Headers.CONTENT_TYPE) != null) {
            return getWrapped().getResponseHeaders().get(Headers.CONTENT_TYPE)
                    .getFirst();
        } else {
            return null;
        }
    }

    @Override
    public AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        return BUFFERED_RESPONSE_DATA;
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getWrapped().getResponseHeaders().put(Headers.CONTENT_TYPE,
                responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        setContentType("application/json");
    }

    /**
     * @return the responseStatusCode
     */
    public int getStatusCode() {
        return getWrapped().getAttachment(STATUS_CODE);
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setStatusCode(int responseStatusCode) {
        getWrapped().putAttachment(STATUS_CODE, responseStatusCode);
    }

    @Override
    protected void setContentLength(int length) {
        wrapped.getResponseHeaders().put(Headers.CONTENT_LENGTH, length);
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
    public void endExchange(int code, T body) throws IOException {
        setStatusCode(code);
        setInError(true);
        writeContent(body);
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
            writeContent(getErrorContent(code,
                    HttpStatus.getStatusText(code),
                    message,
                    t,
                    false));
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    protected abstract T getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException;
}
