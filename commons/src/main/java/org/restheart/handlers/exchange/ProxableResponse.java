/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.handlers.exchange;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 * @param <T>
 */
public abstract class ProxableResponse<T> extends Response<T> {
    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA_KEY
            = AttachmentKey.create(PooledByteBuffer[].class);
    
    protected ProxableResponse(HttpServerExchange exchange) {
        super(exchange);
    }
    
    public abstract T readContent() throws IOException;

    public abstract void writeContent(T content) throws IOException;
    
    public AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        return BUFFERED_RESPONSE_DATA_KEY;
    }

    public PooledByteBuffer[] getRawContent() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Response content is not available. "
                    + "Add a Response Inteceptor with "
                    + "@RegisterPlugin(requiresContent = true) to make "
                    + "the content available.");
        }

        return getWrappedExchange().getAttachment(getRawContentKey());
    }
    
    public void setRawContent(PooledByteBuffer[] raw) {
        getWrappedExchange().putAttachment(getRawContentKey(), raw);
    }
    
    public boolean isContentAvailable() {
        return null != getWrappedExchange().getAttachment(getRawContentKey());
    }
    
    protected void setContentLength(int length) {
        wrapped.getResponseHeaders().put(Headers.CONTENT_LENGTH, length);
    }
    
    /**
     *
     * @param code
     * @param body
     * @throws java.io.IOException
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
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
    
    protected abstract T getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException;
}
