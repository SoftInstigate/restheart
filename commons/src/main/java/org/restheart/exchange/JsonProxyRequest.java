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

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.restheart.utils.BuffersUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonProxyRequest extends ProxyRequest<JsonElement> {
    static {
        LOGGER = LoggerFactory.getLogger(JsonProxyRequest.class);
    }

    protected JsonProxyRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static JsonProxyRequest of(HttpServerExchange exchange) {
        return new JsonProxyRequest(exchange);
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

        if (getWrappedExchange().getAttachment(getRawContentKey()) == null) {
            return JsonNull.INSTANCE;
        } else {
            try {
                return JsonParser.parseString(BuffersUtils.toString(getBuffer(), StandardCharsets.UTF_8));
            } catch (JsonParseException ex) {
                // dump bufferd content
                BuffersUtils.dump("Error parsing content", getBuffer());

                throw new IOException("Error parsing json", ex);
            }
        }
    }

    /**
     * updates the request content
     *
     * allocates the PooledByteBuffer array so close() must be invoked
     * to avoid memory leaks
     */
    @Override
    public void writeContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        if (content == null) {
            setBuffer(null);
            getHeaders().remove(Headers.CONTENT_LENGTH);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getBuffer();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setBuffer(dest);
            }

            int copied = BuffersUtils.transfer(ByteBuffer.wrap(content.toString().getBytes()), dest, wrapped);

            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
