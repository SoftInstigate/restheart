/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChannelReader {
    /**
     *
     * @param exchange
     * @return
     * @throws IOException
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
     *
     * @param exchange
     * @return
     * @throws IOException
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
