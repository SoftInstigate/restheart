/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.metadata.checkers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.checkers.Checker.PHASE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * ContentSizeChecker allows to check the request content length
 *
 * the args arguments is the size condition: a is json object as follows: {
 * "max": MAX_SIZE, "min": MIN_SIZE }
 *
 * sizes are in bytes
 *
 */
public class ContentSizeChecker implements Checker {

    static final Logger LOGGER = LoggerFactory.getLogger(ContentSizeChecker.class);

    @Override
    public boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck,
            BsonValue args) {
        if (args.isDocument()) {
            BsonDocument condition = args.asDocument();

            Integer maxSize = null;
            BsonValue _maxSize = condition.get("max");

            if (_maxSize != null && _maxSize.isInt32()) {
                maxSize = _maxSize.asInt32().getValue();
            } else {
                context.addWarning("checker wrong definition: "
                        + "'max' property missing.");
                return true;
            }

            Integer minSize = null;
            BsonValue _minSize = condition.get("min");

            if (_minSize != null) {
                minSize = _minSize.asNumber().intValue();
            }

            return (minSize == null
                    ? checkSize(exchange, -1, maxSize)
                    : checkSize(exchange, minSize, maxSize));

        } else {
            context.addWarning("checker wrong definition: "
                    + "args property must be a json object "
                    + "with 'min' and 'max' properties.");
            return true;
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

        LOGGER.debug("checkSize({}, {}, {}) -> {}",
                requestLenght,
                minSize,
                maxSize,
                ret);

        return ret;
    }

    @Override
    public PHASE getPhase(RequestContext context) {
        return PHASE.BEFORE_WRITE;
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return true;
    }
}
