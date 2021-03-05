/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */

package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayInterceptor;
import static org.restheart.plugins.InterceptPoint.RESPONSE_ASYNC;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "echoAsyncResponseInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true,
        interceptPoint = RESPONSE_ASYNC)
public class EchoAsyncResponseInterceptor implements ByteArrayInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoAsyncResponseInterceptor.class);

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        try {
            Thread.sleep(2 * 1000);
            LOGGER.info("This log message is written 2 seconds after response "
                    + "by echoAsyncResponseInterceptor");
        } catch (InterruptedException ie) {
            LOGGER.warn("error ", ie);
        }
    }

    @Override
    public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
        return request.getPath().equals("/iecho");
    }
}
