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
package org.restheart.exchange;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;
import org.restheart.utils.HttpStatus;

/**
 * Base class for Response implementation that can be used in proxied requests.
 *
 * It stores the response content in the BUFFERED_RESPONSE_DATA_KEY attachment
 * of the HttpServerExchange.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class ProxyResponse<T> extends Response<T>
        implements BufferedExchange<T> {
    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA_KEY
            = AttachmentKey.create(PooledByteBuffer[].class);

    protected ProxyResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    @Override
    public abstract T readContent() throws IOException;

    @Override

    public abstract void writeContent(T content) throws IOException;

    public AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        return BUFFERED_RESPONSE_DATA_KEY;
    }

    @Override
    public PooledByteBuffer[] getBuffer() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Response content is not available. "
                    + "Add a Response Inteceptor with "
                    + "@RegisterPlugin(requiresContent = true) to make "
                    + "the content available.");
        }

        return getWrappedExchange().getAttachment(getRawContentKey());
    }

    @Override
    public void setBuffer(PooledByteBuffer[] raw) {
        getWrappedExchange().putAttachment(getRawContentKey(), raw);
    }

    @Override
    public boolean isContentAvailable() {
        return null != getWrappedExchange().getAttachment(getRawContentKey());
    }

    protected void setContentLength(int length) {
        getHeaders().put(Headers.CONTENT_LENGTH, length);
    }

    /**
     *
     * @param code
     * @param message
     * @param t
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
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

    /**
     *
     * @param code
     * @param httpStatusText
     * @param message
     * @param t
     * @param includeStackTrace
     * @return the content descibing the error
     * @throws IOException
     */
    protected abstract T getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException;
}
