/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

/**
 * The request body read in advance by the core, attached to the exchange.
 *
 * <p>The core reads the body without blocking the IO thread, with a read
 * listener that holds no thread while the client sends it, in two cases:</p>
 * <ul>
 *   <li>for non-blocking services, which run on the IO thread and cannot block
 *       to read it (see {@code non-blocking-content-limit});</li>
 *   <li>for blocking services, when the body has a Content-Length up to
 *       {@code blocking-content-prebuffer-limit}: the virtual thread then finds
 *       it in memory and never waits for the client.</li>
 * </ul>
 *
 * <p>{@link org.restheart.utils.ChannelReader} and the {@code parseContent()}
 * of the request types read it from here when it is available. Once read in
 * advance, the request channel is at its end.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.15.0
 */
public final class BufferedContent {
    private static final AttachmentKey<byte[]> KEY = AttachmentKey.create(byte[].class);

    private BufferedContent() {
    }

    /**
     * Attaches the request body read in advance to the exchange.
     *
     * @param exchange the exchange
     * @param content the whole request body
     */
    public static void set(HttpServerExchange exchange, byte[] content) {
        exchange.putAttachment(KEY, content);
    }

    /**
     * @param exchange the exchange
     * @return the request body read in advance, or null if it was not
     */
    public static byte[] get(HttpServerExchange exchange) {
        return exchange.getAttachment(KEY);
    }

    /**
     * @param exchange the exchange
     * @return true if the request declares a body: a positive Content-Length or a Transfer-Encoding
     */
    public static boolean hasBody(HttpServerExchange exchange) {
        return exchange.getRequestContentLength() > 0 || exchange.getRequestHeaders().contains(Headers.TRANSFER_ENCODING);
    }
}
