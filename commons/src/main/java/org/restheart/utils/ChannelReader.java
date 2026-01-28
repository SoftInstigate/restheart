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

package org.restheart.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.undertow.server.HttpServerExchange;

/**
 * Utility class for reading data from HTTP server exchange channels.
 * Provides convenient methods to read request body content as strings or byte arrays
 * using Undertow's asynchronous receiver API.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChannelReader {
    /**
     * Reads the complete request body as a string using UTF-8 encoding.
     * This method uses Undertow's asynchronous receiver to read the full request
     * content and convert it to a string.
     *
     * @param exchange the HTTP server exchange containing the request body
     * @return the complete request body as a UTF-8 encoded string
     * @throws IOException if an I/O error occurs during reading
     */
    public static String readString(HttpServerExchange exchange) throws IOException {
        final var receiver = exchange.getRequestReceiver();
        final var ret = new String[1];

        receiver.receiveFullString(
            (_exchange, data) -> ret[0] = data,
            (_exchange, ioe) -> LambdaUtils.throwsSneakyException(ioe),
            StandardCharsets.UTF_8);

        return ret[0];
    }

    /**
     * Reads the complete request body as a byte array.
     * This method uses Undertow's asynchronous receiver to read the full request
     * content as raw bytes.
     *
     * @param exchange the HTTP server exchange containing the request body
     * @return the complete request body as a byte array
     * @throws IOException if an I/O error occurs during reading
     */
    public static byte[] readBytes(HttpServerExchange exchange) throws IOException {
        final var receiver = exchange.getRequestReceiver();
        final var ret = new byte[1][];

        receiver.receiveFullBytes(
            (_exchange, data) -> ret[0] = data,
            (_exchange, ioe) -> LambdaUtils.throwsSneakyException(ioe));

        return ret[0];
    }
}
