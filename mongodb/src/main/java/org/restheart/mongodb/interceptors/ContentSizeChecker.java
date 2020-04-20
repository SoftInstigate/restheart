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
package org.restheart.mongodb.interceptors;

import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.BsonInterceptor;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * ContentSizeChecker allows to check the request content length on documents of
 * collections that have the following metadata:
 * <br><br>
 * { "checkContentSize": { "max": MAX_SIZE, "min": MIN_SIZE } }
 * <br><br>
 * Sizes are in bytes
 * 
 */
@RegisterPlugin(
        name = "checkContentSize",
        description = "Checks the write request content length on documents of collections with 'checkContentSize' metadata",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH)
@SuppressWarnings("deprecation")
public class ContentSizeChecker implements BsonInterceptor {

    static final Logger LOGGER = LoggerFactory.getLogger(ContentSizeChecker.class);

    /**
     *
     */
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        Integer max = request.getCollectionProps()
                .get("checkContentSize")
                .asDocument()
                .get("max")
                .asInt32()
                .intValue();

        Integer min = request.getCollectionProps()
                .get("checkContentSize")
                .asDocument()
                .containsKey("min")
                && request.getCollectionProps()
                        .get("checkContentSize")
                        .asDocument()
                        .get("min")
                        .isInt32()
                        ? request.getCollectionProps()
                                .get("checkContentSize")
                                .asDocument()
                                .get("min")
                                .asInt32()
                                .intValue()
                        : null;

        var check = (min == null
                ? checkSize(request.getExchange(), -1, max)
                : checkSize(request.getExchange(), min, max));

        if (!check) {
            var errorMsg = "Request violates content length constraints: ";

            if (min != null) {
                errorMsg = errorMsg
                        .concat("min size=").concat(min.toString())
                        .concat("max size=").concat(max.toString());
            } else {
                errorMsg = errorMsg
                        .concat("max size=").concat(max.toString());
            }

            response.setInError(HttpStatus.SC_BAD_REQUEST, errorMsg);
        }
    }

    private boolean checkSize(
            HttpServerExchange exchange,
            int minSize,
            int maxSize) {
        long requestLenght = exchange.getRequestContentLength();

        boolean ret = (minSize < 0
                ? requestLenght <= maxSize
                : requestLenght >= minSize && requestLenght <= maxSize);

        LOGGER.trace("checkSize({}, {}, {}) -> {}",
                requestLenght,
                minSize,
                maxSize,
                ret);

        return ret;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isWriteDocument()
                && request.getCollectionProps() != null
                && request.getCollectionProps()
                        .containsKey("checkContentSize")
                && request.getCollectionProps()
                        .get("checkContentSize")
                        .isDocument()
                && request.getCollectionProps()
                        .get("checkContentSize")
                        .asDocument()
                        .containsKey("max")
                && request.getCollectionProps()
                        .get("checkContentSize")
                        .asDocument()
                        .get("max")
                        .isInt32();
    }
}
