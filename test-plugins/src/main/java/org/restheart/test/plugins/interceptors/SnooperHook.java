/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "snooper",
        description = "An example hook that logs request and response info",
        enabledByDefault = false,
        interceptPoint = InterceptPoint.RESPONSE_ASYNC)
@SuppressWarnings("deprecation")
public class SnooperHook implements Interceptor {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(SnooperHook.class);

    @InjectConfiguration
    public void init(Map<String, Object> conf) {
        LOGGER.info("Configuration args {}",
                conf);
    }
    
    /**
     *
     * @param exchange
     */
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        LOGGER.info("Request {} {} {}",
                request.getMethod(),
                exchange.getRequestURI(), exchange.getStatusCode());

        if (response.getDbOperationResult() != null) {
            BsonValue newId = response
                    .getDbOperationResult()
                    .getNewId();

            BsonDocument newData = response
                    .getDbOperationResult()
                    .getNewData();

            BsonDocument oldData = response
                    .getDbOperationResult()
                    .getOldData();

            LOGGER.info("**** New id ****\n{}",
                    newId == null ? null : newId);

            LOGGER.info("**** New data ****\n{}",
                    newData == null ? null : newData.toJson());

            LOGGER.info("**** Old data ****\n{}",
                    oldData == null ? null : oldData.toJson());
        }

        BsonValue responseContent = response.getContent();

        if (responseContent != null) {
            LOGGER.info("*** Response content ****\n{}",
                    JsonUtils.toJson(responseContent));
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return true;//BsonRequest.isInitialized(exchange);
    }
}
