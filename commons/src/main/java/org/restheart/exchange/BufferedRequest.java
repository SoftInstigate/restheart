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
import java.io.IOException;
import java.lang.reflect.Field;

/**
 *
 * A buffered request stores request content in the BUFFERED_REQUEST_DATA
 * attachment of the HttpServerExchange
 *
 * This makes possibile using an concrete implementation of it in proxied
 * request.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 * @param <T>
 */
public abstract class BufferedRequest<T> extends AbstractRequest<T> {
    public BufferedRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public abstract T readContent() throws IOException;

    public abstract void writeContent(T content) throws IOException;
    
    @SuppressWarnings("unchecked")
    protected AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        Field f;

        try {
            f = HttpServerExchange.class.getDeclaredField("BUFFERED_REQUEST_DATA");
            f.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException("could not find BUFFERED_REQUEST_DATA field", ex);
        }

        try {
            return (AttachmentKey<PooledByteBuffer[]>) f.get(getWrappedExchange());
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
        }
    }

    public void setRawContent(PooledByteBuffer[] raw) {
        getWrappedExchange().putAttachment(getRawContentKey(), raw);
    }

    public PooledByteBuffer[] getRawContent() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Request content is not available. "
                    + "Add a Request Inteceptor with "
                    + "@RegisterPlugin(requiresContent = true) to make "
                    + "the content available.");
        }

        return getWrappedExchange().getAttachment(getRawContentKey());
    }

    public boolean isContentAvailable() {

        return null != getWrappedExchange().getAttachment(getRawContentKey());
    }
}
