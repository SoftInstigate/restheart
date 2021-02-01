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

import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "snooper",
        description = "A test hook that logs the request and response content and, for write requests, the response.getDbOperationResult()",
        enabledByDefault = false,
        interceptPoint = InterceptPoint.RESPONSE_ASYNC)
public class SnooperHook implements MongoInterceptor {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(SnooperHook.class);

    @InjectConfiguration
    public void init(Map<String, Object> conf) {
        LOGGER.debug("Configuration args {}", conf);
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        LOGGER.info("Request {} {} {}",
                request.getMethod(),
                request.getPath(), response.getStatusCode());

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
            LOGGER.info("*** Response content ****\n{}", BsonUtils.toJson(responseContent));
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo");
    }
}
